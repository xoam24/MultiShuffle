package cz.xoam24.multishuffle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class GameManager {

    // Countdown starts at this many seconds before end of round
    private static final int COUNTDOWN_START = 15;

    private final MultiShuffle plugin;
    private GameSession currentSession;
    private BukkitTask  gameTask;

    public GameManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── start / stop / skip ───────────────────────────────────────────────────

    public void startGame(GameSession.Type type, GameSession.Mode mode) {
        if (currentSession != null) return;

        ConfigManager cfg = plugin.getConfigManager();
        currentSession = new GameSession(type, mode, cfg.getDefaultRounds(), cfg.getDefaultRoundTime());

        if (mode == GameSession.Mode.SAME) {
            List<String> pool = type == GameSession.Type.ITEM ? cfg.getItems() : cfg.getBlocks();
            currentSession.initSharedPool(pool);
        }

        // assignTargets PŘED showScoreboards — placeholder musí mít data okamžitě
        assignTargets();
        plugin.getScoreboardManager().showScoreboards();
        plugin.getScoreboardManager().updateScoreboards();

        Bukkit.broadcast(cfg.getMessage("game_started",
                Placeholder.parsed("mode", mode.name())));

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentSession == null) { cancel(); return; }

                // ItemShuffle — průběžná kontrola inventáře
                if (currentSession.getType() == GameSession.Type.ITEM) {
                    checkInventories();
                }

                currentSession.decrementTime();
                plugin.getScoreboardManager().updateScoreboards();

                int timeLeft = currentSession.getRemainingSeconds();

                // Countdown titly (bez prefixu, jen číslo)
                if (timeLeft > 0 && timeLeft <= COUNTDOWN_START) {
                    showCountdownTitle(timeLeft);
                }

                if (timeLeft <= 0) {
                    boolean isLastRound = currentSession.getCurrentRound() >= currentSession.getMaxRounds();
                    showRoundEndTitle(isLastRound);

                    if (isLastRound) {
                        // Odložíme endGame o 3s aby title byl vidět
                        Bukkit.getScheduler().runTaskLater(plugin, GameManager.this::endGame, 60L);
                        cancel();
                    } else {
                        // Nové kolo startuje po 3s (title je vidět)
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (currentSession == null) return;
                            currentSession.incrementRound();
                            currentSession.setRemainingSeconds(
                                    plugin.getConfigManager().getDefaultRoundTime());
                            assignTargets();
                            plugin.getScoreboardManager().updateScoreboards();
                        }, 60L);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stopGame() {
        if (currentSession == null) return;
        if (gameTask != null) { gameTask.cancel(); gameTask = null; }
        currentSession = null;
        plugin.getScoreboardManager().hideScoreboards();
        Bukkit.broadcast(plugin.getConfigManager().getMessage("game_stopped"));
    }

    public void skipRound() {
        if (currentSession == null) return;
        // Nastavíme na 1 — timer odečte na 0 a spustí standardní round-end logiku
        currentSession.setRemainingSeconds(1);
        Bukkit.broadcast(plugin.getConfigManager().getMessage("round_skipped"));
    }

    // ── success handling ──────────────────────────────────────────────────────

    /** Voláno z GameListeneru i z checkInventories(). */
    public void handleSuccess(Player player, Material material) {
        if (currentSession == null) return;
        if (currentSession.hasFinished(player.getUniqueId())) return;

        String targetKey = currentSession.getTarget(player.getUniqueId());
        if (targetKey == null) return;
        if (!targetKey.equalsIgnoreCase(material.getKey().toString())) return;

        currentSession.addPoint(player.getUniqueId());
        currentSession.markFinished(player.getUniqueId());
        giveAbility(player);

        String prettyName = material.name().replace('_', ' ').toLowerCase();

        player.sendMessage(plugin.getConfigManager().getMessage("target_found",
                Placeholder.parsed("target", prettyName)));

        Bukkit.broadcast(plugin.getConfigManager().getMessage("target_broadcast",
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("target", prettyName)));

        plugin.getScoreboardManager().updateScoreboards();
    }

    // ── assign targets ────────────────────────────────────────────────────────

    public void assignTargets() {
        if (currentSession == null) return;
        currentSession.resetFinishedThisRound();

        List<String> pool = currentSession.getType() == GameSession.Type.ITEM
                ? plugin.getConfigManager().getItems()
                : plugin.getConfigManager().getBlocks();

        if (pool == null || pool.isEmpty()) {
            plugin.getLogger().warning(
                    "[MultiShuffle] Pool je prázdný! Zkontroluj config.yml — sekce 'items:' nebo 'blocks:'.");
            return;
        }

        if (currentSession.getMode() == GameSession.Mode.SAME) {
            String shared = currentSession.pickNextSharedTarget();
            if (shared == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                currentSession.setTarget(p.getUniqueId(), shared);
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String target = currentSession.pickUniqueTargetForPlayer(p.getUniqueId(), pool);
                if (target != null) currentSession.setTarget(p.getUniqueId(), target);
            }
        }
    }

    // ── titles ────────────────────────────────────────────────────────────────

    /**
     * Countdown title: zobrazuje jen číslo — BEZ prefixu, BEZ žádného extra textu.
     * Tick sound při každé sekundě odpočtu.
     */
    private void showCountdownTitle(int seconds) {
        ConfigManager cfg = plugin.getConfigManager();

        // Title je prázdný (nebo custom z configu) — subtitle je jen číslo
        Component titleComp    = cfg.parse(cfg.getRaw("countdown_title"));
        Component subtitleComp = cfg.parse(cfg.getRaw("countdown_subtitle"),
                Placeholder.parsed("time",  String.valueOf(seconds)),
                Placeholder.parsed("round", String.valueOf(currentSession.getCurrentRound())));

        Title title = Title.title(
                titleComp,
                subtitleComp,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500))
        );

        Sound tickSound = seconds <= 5 ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.UI_BUTTON_CLICK;
        float pitch     = seconds <= 5 ? (1.0f + (5 - seconds) * 0.1f) : 1.0f;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), tickSound, 0.8f, pitch);
        }
    }

    /**
     * Title po skončení kola / hry.
     * isLastRound=true → "Konec hry", false → "Konec kola".
     * Hraje zvuk ENTITY_PLAYER_LEVELUP.
     */
    private void showRoundEndTitle(boolean isLastRound) {
        ConfigManager cfg = plugin.getConfigManager();

        String titleKey    = isLastRound ? "game_end_title"    : "round_end_title";
        String subtitleKey = isLastRound ? "game_end_subtitle" : "round_end_subtitle";

        Component titleComp    = cfg.parse(cfg.getRaw(titleKey),
                Placeholder.parsed("round",    String.valueOf(currentSession.getCurrentRound())),
                Placeholder.parsed("maxround", String.valueOf(currentSession.getMaxRounds())));
        Component subtitleComp = cfg.parse(cfg.getRaw(subtitleKey),
                Placeholder.parsed("round",    String.valueOf(currentSession.getCurrentRound())),
                Placeholder.parsed("maxround", String.valueOf(currentSession.getMaxRounds())));

        Title title = Title.title(
                titleComp,
                subtitleComp,
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))
        );

        Sound endSound = isLastRound ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), endSound, 1.0f, 1.0f);
        }
    }

    // ── end game ──────────────────────────────────────────────────────────────

    private void endGame() {
        if (currentSession == null) return;

        List<Map.Entry<UUID, Integer>> sorted = currentSession.getAllPoints().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        currentSession = null;
        plugin.getScoreboardManager().hideScoreboards();

        Bukkit.broadcast(plugin.getConfigManager().getMessage("game_ending"));

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                switch (step) {
                    case 1 -> { if (sorted.size() >= 3) announcePlace(sorted.get(2), 3, "place_3"); }
                    case 2 -> { if (sorted.size() >= 2) announcePlace(sorted.get(1), 2, "place_2"); }
                    case 3 -> {
                        if (!sorted.isEmpty()) {
                            announcePlace(sorted.get(0), 1, "place_1");
                            saveStats(sorted);
                        }
                        cancel();
                        return;
                    }
                    default -> { if (step > 3) { cancel(); return; } }
                }
                step++;
            }
        }.runTaskTimer(plugin, 60L, 60L);
    }

    private void announcePlace(Map.Entry<UUID, Integer> entry, int place, String cfgKey) {
        Player p    = Bukkit.getPlayer(entry.getKey());
        String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName();
        ConfigManager cfg = plugin.getConfigManager();

        Component placeComp = cfg.parse(cfg.getRaw(cfgKey));
        Component msg = cfg.getMessage("place_format",
                Placeholder.component("place",  placeComp),
                Placeholder.parsed("player",    name != null ? name : "Unknown"),
                Placeholder.parsed("points",    String.valueOf(entry.getValue())));

        Bukkit.broadcast(msg);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
            if (place == 1) {
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }
    }

    private void saveStats(List<Map.Entry<UUID, Integer>> sorted) {
        if (sorted.isEmpty()) return;
        UUID winner = sorted.get(0).getKey();
        for (Map.Entry<UUID, Integer> e : sorted) {
            plugin.getSqliteManager().savePlayerStatsAsync(
                    e.getKey(),
                    Bukkit.getOfflinePlayer(e.getKey()).getName(),
                    e.getKey().equals(winner),
                    e.getValue());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void checkInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (currentSession.hasFinished(p.getUniqueId())) continue;
            String targetKey = currentSession.getTarget(p.getUniqueId());
            if (targetKey == null) continue;
            Material mat = Material.matchMaterial(targetKey);
            if (mat != null && p.getInventory().contains(mat)) {
                handleSuccess(p, mat);
            }
        }
    }

    public void giveAbility(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.getConfig().getBoolean("abilities.enabled", true)) return;

        List<String> abilities = cfg.getAbilities();
        if (abilities.isEmpty()) return;

        String[] parts = abilities.get(new Random().nextInt(abilities.size())).split(":");
        if (parts.length != 3) return;

        PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE
                .get(NamespacedKey.minecraft(parts[0].toLowerCase()));
        if (type == null) return;

        player.addPotionEffect(new PotionEffect(
                type,
                Integer.parseInt(parts[2]) * 20,
                Integer.parseInt(parts[1]) - 1));
    }

    public GameSession getCurrentSession() { return currentSession; }
}