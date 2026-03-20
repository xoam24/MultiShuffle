package cz.xoam24.multishuffle;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameManager {

    private final MultiShuffle plugin;

    // Aktuálně běžící session — null = žádná hra
    private volatile GameSession session;
    // Hlavní herní timer
    private BukkitTask gameTask;

    // Mezipaměť aktuálního módu pro každý typ (přetrvává mezi hrami)
    private final Map<GameSession.Type, GameSession.Mode> savedModes = new ConcurrentHashMap<>();

    public GameManager(MultiShuffle plugin) {
        this.plugin = plugin;
        savedModes.put(GameSession.Type.ITEM,  GameSession.Mode.RANDOM);
        savedModes.put(GameSession.Type.BLOCK, GameSession.Mode.RANDOM);
    }

    // ── public API ────────────────────────────────────────────────────────────

    public GameSession getCurrentSession()            { return session; }
    public GameSession.Mode getMode(GameSession.Type t){ return savedModes.get(t); }

    public void setMode(GameSession.Type type, GameSession.Mode mode) {
        savedModes.put(type, mode);
    }

    // ── start ─────────────────────────────────────────────────────────────────

    public void startGame(GameSession.Type type) {
        if (session != null) return;

        ConfigManager cfg  = plugin.getConfigManager();
        GameSession.Mode m = savedModes.get(type);

        session = new GameSession(type, m, cfg.getDefaultRounds(), cfg.getDefaultRoundTime());

        // Inicializace shared pool pro SAME mode
        if (m == GameSession.Mode.SAME) {
            session.initSharedPool(getPool(type));
        }

        assignTargets();
        plugin.getScoreboardManager().showScoreboards();
        plugin.getScoreboardManager().updateScoreboards();

        Bukkit.broadcast(cfg.msg("game_started",
                Placeholder.parsed("type",  type.name()),
                Placeholder.parsed("mode",  m.name().toLowerCase()),
                Placeholder.parsed("rounds",String.valueOf(cfg.getDefaultRounds()))));

        plugin.getSoundManager().broadcast("game_start");
        startLoop();
    }

    // ── stop ──────────────────────────────────────────────────────────────────

    public void stopGame() {
        if (session == null) return;
        cancelTask();
        session = null;
        plugin.getScoreboardManager().hideScoreboards();
        Bukkit.broadcast(plugin.getConfigManager().msg("game_stopped"));
        plugin.getSoundManager().broadcast("game_stop");
    }

    // ── skip ──────────────────────────────────────────────────────────────────

    public void skipRound() {
        if (session == null) return;
        if (session.isTransitioning()) return; // nelze skipovat během přechodu
        // Timer se nastaví na 1 — loop ho v příší tiku odečte na 0 → spustí handleRoundEnd
        session.setRemainingSeconds(1);
        Bukkit.broadcast(plugin.getConfigManager().msg("round_skipped"));
    }

    // ── points commands ───────────────────────────────────────────────────────

    /** /ms points set <player|@a> <value> */
    public void setPoints(UUID uuid, int value) {
        if (session == null) return;
        session.setPoints(uuid, value);
        plugin.getScoreboardManager().updateScoreboards();
    }

    /** /ms points set @a <value> */
    public void setPointsAll(int value) {
        if (session == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            session.setPoints(p.getUniqueId(), value);
        }
        plugin.getScoreboardManager().updateScoreboards();
    }

    // ── herní smyčka ──────────────────────────────────────────────────────────

    private void startLoop() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session == null)             { cancel(); return; }
                // Pokud probíhá přechod kol — smyčka čeká, nic nedělá
                if (session.isTransitioning())   { return; }

                // ItemShuffle: průběžná kontrola inventáře (batch, efektivní)
                if (session.getType() == GameSession.Type.ITEM) {
                    checkInventories();
                }

                session.decrementTime();
                plugin.getScoreboardManager().updateScoreboards();

                int t = session.getRemainingSeconds();

                // Countdown titly + zvuky
                if (t > 0 && t <= plugin.getConfigManager().getCountdownStartAt()) {
                    showCountdownTitle(t);
                    plugin.getSoundManager().playCountdown(t);
                }

                // Konec kola
                if (t <= 0) {
                    handleRoundEnd();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Voláno PŘESNĚ jednou při t=0.
     * Guard (isTransitioning) zabraňuje jakémukoli dalšímu spuštění.
     */
    private void handleRoundEnd() {
        if (session == null)              return;
        if (session.isTransitioning())    return; // double-fire guard

        session.setTransitioning(true);  // ZAMKNOUT — od teď smyčka jen čeká

        boolean isLast = session.getCurrentRound() >= session.getMaxRounds();
        showRoundEndTitle(isLast);
        plugin.getSoundManager().broadcast(isLast ? "game_end" : "round_end");

        long transitionTicks = plugin.getConfigManager().getRoundTransitionMs() / 50;

        if (isLast) {
            cancelTask();
            Bukkit.getScheduler().runTaskLater(plugin, this::runEndGame, transitionTicks);
        } else {
            // Nové kolo po pauze — smyčka stále běží (isTransitioning = true drží ji idle)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (session == null) return;
                session.incrementRound();
                session.setRemainingSeconds(plugin.getConfigManager().getDefaultRoundTime());
                assignTargets();                          // resetuje i isTransitioning = false
                plugin.getScoreboardManager().updateScoreboards();
                showNewRoundTitle();
                plugin.getSoundManager().broadcast("new_round");
            }, transitionTicks);
        }
    }

    // ── success ───────────────────────────────────────────────────────────────

    /**
     * Centrální metoda pro úspěch hráče.
     * Thread-safe pomocí synchronized bloku nad session.
     */
    public void handleSuccess(Player player, Material material) {
        GameSession s = session;
        if (s == null)                             return;
        if (s.isTransitioning())                   return;

        UUID uuid = player.getUniqueId();
        // Double-check v synchronized bloku — důležité pro 100+ hráčů
        synchronized (s) {
            if (s.hasFinished(uuid))               return;
            String targetKey = s.getTarget(uuid);
            if (targetKey == null)                 return;
            if (!targetKey.equalsIgnoreCase(material.getKey().toString())) return;

            s.addPoint(uuid);
            s.markFinished(uuid);
        }

        giveAbility(player);

        String prettyName = material.name().replace('_', ' ').toLowerCase();
        player.sendMessage(plugin.getConfigManager().msg("target_found",
                Placeholder.parsed("target", prettyName)));
        Bukkit.broadcast(plugin.getConfigManager().msg("target_broadcast",
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("target", prettyName)));

        plugin.getSoundManager().play("success_self",  player);
        plugin.getSoundManager().play("success_others",
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player))
                        .collect(Collectors.toList()));

        plugin.getScoreboardManager().updateScoreboards();
    }

    // ── assign targets ────────────────────────────────────────────────────────

    public void assignTargets() {
        GameSession s = session;
        if (s == null) return;

        s.resetRound(); // resetuje finishedThisRound + isTransitioning

        List<String> pool = getPool(s.getType());
        if (pool.isEmpty()) {
            plugin.getLogger().severe("[MultiShuffle] Pool je prázdný! Sekce '"
                    + (s.getType() == GameSession.Type.ITEM ? "items" : "blocks") + "' v config.yml.");
            stopGame();
            return;
        }

        if (s.getMode() == GameSession.Mode.SAME) {
            String shared = s.pickNextShared();
            if (shared == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                s.setTarget(p.getUniqueId(), shared);
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String t = s.pickUniqueTarget(p.getUniqueId(), pool);
                if (t != null) s.setTarget(p.getUniqueId(), t);
            }
        }
    }

    /** Přiřadí target jednomu hráči (mid-game join). */
    public void assignTargetForPlayer(Player player) {
        GameSession s = session;
        if (s == null) return;
        if (s.getTarget(player.getUniqueId()) != null) return; // již má

        List<String> pool = getPool(s.getType());
        if (pool.isEmpty()) return;

        if (s.getMode() == GameSession.Mode.SAME) {
            // Dáme mu stejný target co mají ostatní
            String shared = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .map(p -> s.getTarget(p.getUniqueId()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(s.pickNextShared());
            if (shared != null) s.setTarget(player.getUniqueId(), shared);
        } else {
            String t = s.pickUniqueTarget(player.getUniqueId(), pool);
            if (t != null) s.setTarget(player.getUniqueId(), t);
        }
    }

    // ── titles ────────────────────────────────────────────────────────────────

    private void showCountdownTitle(int seconds) {
        ConfigManager cfg = plugin.getConfigManager();
        Title title = cfg.buildTitle(
                "countdown_title",
                "countdown_subtitle",
                "titles.countdown",
                Placeholder.parsed("time",  String.valueOf(seconds)),
                Placeholder.parsed("round", String.valueOf(session.getCurrentRound())));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
    }

    private void showRoundEndTitle(boolean isLast) {
        ConfigManager cfg = plugin.getConfigManager();
        String tKey  = isLast ? "game_end_title"    : "round_end_title";
        String stKey = isLast ? "game_end_subtitle" : "round_end_subtitle";
        String tPath = isLast ? "titles.game_end"   : "titles.round_end";

        Title title = cfg.buildTitle(tKey, stKey, tPath,
                Placeholder.parsed("round",    String.valueOf(session.getCurrentRound())),
                Placeholder.parsed("maxround", String.valueOf(session.getMaxRounds())));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
    }

    private void showNewRoundTitle() {
        if (session == null) return;
        ConfigManager cfg = plugin.getConfigManager();
        Title title = cfg.buildTitle(
                "new_round_title",
                "new_round_subtitle",
                "titles.new_round",
                Placeholder.parsed("round",    String.valueOf(session.getCurrentRound())),
                Placeholder.parsed("maxround", String.valueOf(session.getMaxRounds())));
        for (Player p : Bukkit.getOnlinePlayers()) p.showTitle(title);
    }

    // ── end game ──────────────────────────────────────────────────────────────

    private void runEndGame() {
        GameSession s = session;
        if (s == null) return;

        // Seřadit hráče
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(s.getAllPoints().entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        session = null;
        plugin.getScoreboardManager().hideScoreboards();

        Bukkit.broadcast(plugin.getConfigManager().msg("game_ending"));

        ConfigManager cfg    = plugin.getConfigManager();
        int   maxW           = Math.min(cfg.getMaxWinners(), sorted.size());
        long  delayTicks     = cfg.getWinnerDelayTicks();
        boolean ascending    = cfg.isWinnersAscending();  // true = 3→2→1, false = 1→2→3

        // Sestavit pořadí výherců
        List<Map.Entry<UUID, Integer>> toAnnounce = new ArrayList<>(sorted.subList(0, maxW));
        if (ascending) Collections.reverse(toAnnounce); // pak bude 1. místo poslední = nejdramatičtější

        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= toAnnounce.size()) {
                    saveStats(sorted);
                    cancel();
                    return;
                }
                // Zjistit skutečné pořadí (pozice v sorted listu, 1-indexed)
                Map.Entry<UUID, Integer> entry = toAnnounce.get(i);
                int place = sorted.indexOf(entry) + 1;
                announcePlace(entry, place);
                i++;
            }
        }.runTaskTimer(plugin, delayTicks, delayTicks);
    }

    private void announcePlace(Map.Entry<UUID, Integer> entry, int place) {
        Player p    = Bukkit.getPlayer(entry.getKey());
        String name = p != null ? p.getName()
                : Objects.toString(Bukkit.getOfflinePlayer(entry.getKey()).getName(), "Unknown");

        ConfigManager cfg   = plugin.getConfigManager();
        String placeKey     = "place_" + place;
        String placeFallback = "#" + place;

        String rawPlace = cfg.raw(placeKey);
        if (rawPlace.isEmpty()) rawPlace = placeFallback;

        Bukkit.broadcast(cfg.msg("place_format",
                Placeholder.parsed("place",  rawPlace),
                Placeholder.parsed("player", name),
                Placeholder.parsed("points", String.valueOf(entry.getValue())),
                Placeholder.parsed("rank",   String.valueOf(place))));

        String soundKey = place == 1 ? "winner_first"
                : place == 2 ? "winner_second"
                : "winner_other";
        plugin.getSoundManager().broadcast(soundKey);
    }

    private void saveStats(List<Map.Entry<UUID, Integer>> sorted) {
        if (sorted.isEmpty()) return;
        UUID winner = sorted.get(0).getKey();
        for (Map.Entry<UUID, Integer> e : sorted) {
            plugin.getSqliteManager().savePlayerStatsAsync(
                    e.getKey(),
                    Objects.toString(Bukkit.getOfflinePlayer(e.getKey()).getName(), "Unknown"),
                    e.getKey().equals(winner),
                    e.getValue());
        }
    }

    // ── abilities ─────────────────────────────────────────────────────────────

    /**
     * Abilities formát: "effect_name:amplifier:duration_sec"
     * amplifier je 1-based (1 = level I).
     */
    public void giveAbility(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getConfig().getBoolean("abilities.enabled", false)) return;

        List<String> list = cfg.getAbilities();
        if (list.isEmpty()) return;

        String raw = list.get(new Random().nextInt(list.size())).trim();
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            plugin.getLogger().warning("[MultiShuffle] Neplatná ability: '" + raw
                    + "' — formát: effect_name:amplifier:duration_sec");
            return;
        }

        PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE
                .get(NamespacedKey.minecraft(parts[0].trim().toLowerCase()));
        if (type == null) {
            plugin.getLogger().warning("[MultiShuffle] Neznámý potion effect: '" + parts[0] + "'");
            return;
        }

        int amplifier, durationTicks;
        try {
            amplifier      = Math.max(0, Integer.parseInt(parts[1].trim()) - 1);
            durationTicks  = Integer.parseInt(parts[2].trim()) * 20;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[MultiShuffle] Neplatné číslo v ability: '" + raw + "'");
            return;
        }

        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true));
        player.sendMessage(cfg.msg("ability_received",
                Placeholder.parsed("effect",    parts[0].trim().replace('_', ' ').toLowerCase()),
                Placeholder.parsed("duration",  parts[2].trim())));
        plugin.getSoundManager().play("ability_received", player);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<String> getPool(GameSession.Type type) {
        return type == GameSession.Type.ITEM
                ? plugin.getConfigManager().getItems()
                : plugin.getConfigManager().getBlocks();
    }

    /** Batch kontrola inventářů — O(n) kde n=online hráči, ne O(n*inventorySize). */
    private void checkInventories() {
        GameSession s = session;
        if (s == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (s.hasFinished(p.getUniqueId())) continue;
            String targetKey = s.getTarget(p.getUniqueId());
            if (targetKey == null) continue;
            Material mat = Material.matchMaterial(targetKey);
            if (mat != null && p.getInventory().contains(mat)) {
                handleSuccess(p, mat);
            }
        }
    }

    private void cancelTask() {
        if (gameTask != null) { gameTask.cancel(); gameTask = null; }
    }
}