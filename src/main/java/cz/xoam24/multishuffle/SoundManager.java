package cz.xoam24.multishuffle;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Centralizovaný zvukový systém.
 * Všechny zvuky jsou konfigurovatelné v config.yml pod sekcí "sounds".
 *
 * Formát v configu:
 * sounds:
 *   game_start:
 *     sound: ENTITY_ENDER_DRAGON_GROWL
 *     volume: 0.6
 *     pitch:  1.2
 *     enabled: true
 */
public class SoundManager {

    private final MultiShuffle plugin;

    public SoundManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── public API ────────────────────────────────────────────────────────────

    /** Přehraje zvuk všem online hráčům. */
    public void broadcast(String key) {
        play(key, Bukkit.getOnlinePlayers());
    }

    /** Přehraje zvuk jednomu hráči. */
    public void play(String key, Player player) {
        play(key, java.util.Collections.singletonList(player));
    }

    /** Přehraje zvuk kolekci hráčů. */
    public void play(String key, Collection<? extends Player> players) {
        ConfigurationSection cfg = plugin.getConfigManager().getConfig()
                .getConfigurationSection("sounds." + key);
        if (cfg == null) return;
        if (!cfg.getBoolean("enabled", true)) return;

        String soundName = cfg.getString("sound", "");
        if (soundName.isEmpty()) return;

        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[MultiShuffle] Neznámý zvuk: '" + soundName
                    + "' (sounds." + key + ")");
            return;
        }

        float volume = (float) cfg.getDouble("volume", 1.0);
        float pitch  = (float) cfg.getDouble("pitch", 1.0);

        for (Player p : players) {
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
            }
        }
    }

    /**
     * Přehraje countdown zvuk s dynamickým pitchem.
     * Posledních 5s pitch stoupá pro dramatický efekt.
     */
    public void playCountdown(int secondsLeft) {
        String key = secondsLeft <= 5 ? "countdown_final" : "countdown_tick";
        ConfigurationSection cfg = plugin.getConfigManager().getConfig()
                .getConfigurationSection("sounds." + key);
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;

        String soundName = cfg.getString("sound", "UI_BUTTON_CLICK");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        float volume = (float) cfg.getDouble("volume", 0.8);
        // Dynamický pitch: 0.8 na 5s → 1.2 na 1s
        float basePitch = (float) cfg.getDouble("pitch", 1.0);
        float pitch = secondsLeft <= 5
                ? basePitch + (5 - secondsLeft) * 0.1f
                : basePitch;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
        }
    }
}