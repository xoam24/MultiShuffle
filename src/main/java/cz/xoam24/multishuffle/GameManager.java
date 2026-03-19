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

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class GameManager {

    private final MultiShuffle plugin;
    private GameSession currentSession;
    private BukkitRunnable gameTask;

    public GameManager(MultiShuffle plugin) { this.plugin = plugin; }

    public void startGame(GameSession.Type type, GameSession.Mode mode) {
        if (currentSession != null) return;
        ConfigManager config = plugin.getConfigManager();
        currentSession = new GameSession(type, mode, config.getDefaultRounds(), config.getDefaultRoundTime());

        // Pro SAME mode inicializujeme sdílený pool hned na začátku
        if (mode == GameSession.Mode.SAME) {
            List<String> pool = type == GameSession.Type.ITEM
                    ? config.getItems()
                    : config.getBlocks();
            currentSession.initSharedPool(pool);
        }

        // OPRAVA: assignTargets PŘED showScoreboards, aby placeholder měl data hned
        assignTargets();
        plugin.getScoreboardManager().showScoreboards();
        // Okamžitě zaktualizovat scoreboard s přiřazenými targety
        plugin.getScoreboardManager().updateScoreboards();

        Bukkit.broadcast(config.getMessage("game_started", Placeholder.parsed("mode", mode.name())));

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (currentSession == null) { this.cancel(); return; }

                // Kontrola Itemů v inventáři pro ItemShuffle
                if (currentSession.getType() == GameSession.Type.ITEM) {
                    checkInventories();
                }

                currentSession.decrementTime();
                plugin.getScoreboardManager().updateScoreboards();
                int time = currentSession.getRemainingSeconds();

                if (time == 15 || time == 10 || (time <= 5 && time > 0)) {
                    showCountdownTitle(time);
                }

                if (time <= 0) {
                    if (currentSession.getCurrentRound() >= currentSession.getMaxRounds()) {
                        endGame(); this.cancel();
                    } else {
                        currentSession.incrementRound();
                        currentSession.setRemainingSeconds(plugin.getConfigManager().getDefaultRoundTime());
                        assignTargets();
                        // Okamžitě zaktualizovat scoreboard s novými targety
                        plugin.getScoreboardManager().updateScoreboards();
                    }
                }
            }
        };
        gameTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void checkInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (currentSession.hasFinished(p.getUniqueId())) continue;

            String targetKey = currentSession.getTarget(p.getUniqueId());
            if (targetKey == null) continue;

            Material targetMat = Material.matchMaterial(targetKey);
            if (targetMat != null && p.getInventory().contains(targetMat)) {
                handleSuccess(p, targetMat);
            }
        }
    }

    public void handleSuccess(Player player, Material material) {
        if (currentSession == null || currentSession.hasFinished(player.getUniqueId())) return;

        String targetKey = currentSession.getTarget(player.getUniqueId());
        if (targetKey == null || !targetKey.equalsIgnoreCase(material.getKey().toString())) return;

        currentSession.addPoint(player.getUniqueId());
        currentSession.markFinished(player.getUniqueId());
        giveAbility(player);

        String prettyName = material.name().replace("_", " ").toLowerCase();

        player.sendMessage(plugin.getConfigManager().getMessage("target_found",
                Placeholder.parsed("target", prettyName)));

        Bukkit.broadcast(plugin.getConfigManager().getMessage("target_broadcast",
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("target", prettyName)));

        plugin.getScoreboardManager().updateScoreboards();
    }

    public void stopGame() {
        if (currentSession == null) return;
        if (gameTask != null) gameTask.cancel();
        currentSession = null;
        plugin.getScoreboardManager().hideScoreboards();
        Bukkit.broadcast(plugin.getConfigManager().getMessage("game_stopped"));
    }

    public void skipRound() {
        if (currentSession != null) {
            currentSession.setRemainingSeconds(1);
            Bukkit.broadcast(plugin.getConfigManager().getMessage("round_skipped"));
        }
    }

    private void showCountdownTitle(int seconds) {
        ConfigManager config = plugin.getConfigManager();
        Component titleStr = config.parseComponent(config.getRawMessage("countdown_title"));
        Component subtitleStr = config.getMessage("countdown_subtitle",
                Placeholder.parsed("time", String.valueOf(seconds)),
                Placeholder.parsed("multishuffle_round", String.valueOf(currentSession.getCurrentRound()))
        );
        Title title = Title.title(titleStr, subtitleStr,
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(500)));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }
    }

    private void endGame() {
        if (currentSession == null) return;
        List<Map.Entry<UUID, Integer>> sortedPlayers = currentSession.getAllPoints().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        currentSession = null;
        plugin.getScoreboardManager().hideScoreboards();
        if (gameTask != null) gameTask.cancel();

        Bukkit.broadcast(plugin.getConfigManager().getMessage("game_ending"));

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                String placeKey = step == 1 ? "place_3" : (step == 2 ? "place_2" : (step == 3 ? "place_1" : ""));
                if (step == 1 && sortedPlayers.size() >= 3) announcePlace(sortedPlayers.get(2), 3, placeKey);
                else if (step == 2 && sortedPlayers.size() >= 2) announcePlace(sortedPlayers.get(1), 2, placeKey);
                else if (step == 3 && sortedPlayers.size() >= 1) {
                    announcePlace(sortedPlayers.get(0), 1, placeKey);
                    saveStats(sortedPlayers);
                    this.cancel();
                } else if (step > 3 || sortedPlayers.isEmpty()) this.cancel();
                step++;
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    private void announcePlace(Map.Entry<UUID, Integer> entry, int place, String placeConfigKey) {
        Player p = Bukkit.getPlayer(entry.getKey());
        String name = p != null ? p.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName();
        ConfigManager conf = plugin.getConfigManager();

        Component placeComponent = conf.parseComponent(conf.getRawMessage(placeConfigKey));
        Component msg = conf.getMessage("place_format",
                Placeholder.component("place", placeComponent),
                Placeholder.parsed("player", name != null ? name : "Unknown"),
                Placeholder.parsed("points", String.valueOf(entry.getValue()))
        );
        Bukkit.broadcast(msg);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
            if (place == 1) online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    private void saveStats(List<Map.Entry<UUID, Integer>> sortedPlayers) {
        if (sortedPlayers.isEmpty()) return;
        UUID winnerUuid = sortedPlayers.get(0).getKey();
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            UUID uuid = entry.getKey();
            plugin.getSqliteManager().savePlayerStatsAsync(uuid,
                    Bukkit.getOfflinePlayer(uuid).getName(),
                    uuid.equals(winnerUuid),
                    entry.getValue());
        }
    }

    public void assignTargets() {
        if (currentSession == null) return;
        currentSession.resetFinishedThisRound();

        List<String> pool = currentSession.getType() == GameSession.Type.ITEM
                ? plugin.getConfigManager().getItems()
                : plugin.getConfigManager().getBlocks();

        if (pool == null || pool.isEmpty()) {
            plugin.getLogger().warning("[MultiShuffle] Pool je prázdný! Zkontroluj config.yml - sekce 'items:' nebo 'blocks:'");
            return;
        }

        if (currentSession.getMode() == GameSession.Mode.SAME) {
            // SAME mode: všichni dostanou stejný target ze sdíleného poolu
            String sharedTarget = currentSession.pickNextSharedTarget();
            if (sharedTarget == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                currentSession.setTarget(p.getUniqueId(), sharedTarget);
            }
        } else {
            // RANDOM mode: každý hráč dostane unikátní target, který ještě nedostal
            for (Player p : Bukkit.getOnlinePlayers()) {
                String target = currentSession.pickUniqueTargetForPlayer(p.getUniqueId(), pool);
                if (target != null) {
                    currentSession.setTarget(p.getUniqueId(), target);
                }
            }
        }
    }

    public void giveAbility(Player player) {
        if (!plugin.getConfigManager().getConfig().getBoolean("abilities.enabled", true)) return;
        List<String> abilities = plugin.getConfigManager().getAbilities();
        if (abilities.isEmpty()) return;
        String[] parts = abilities.get(new Random().nextInt(abilities.size())).split(":");
        if (parts.length == 3) {
            PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                    NamespacedKey.minecraft(parts[0].toLowerCase()));
            if (type != null) player.addPotionEffect(new PotionEffect(
                    type, Integer.parseInt(parts[2]) * 20, Integer.parseInt(parts[1]) - 1));
        }
    }

    public GameSession getCurrentSession() { return currentSession; }
}