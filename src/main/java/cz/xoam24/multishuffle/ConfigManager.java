package cz.xoam24.multishuffle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.util.List;

public class ConfigManager {

    private final MultiShuffle plugin;
    private final MiniMessage   mm = MiniMessage.miniMessage();

    public ConfigManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    public void reload()               { plugin.reloadConfig(); }
    public FileConfiguration getConfig(){ return plugin.getConfig(); }

    // ── zprávy ───────────────────────────────────────────────────────────────

    /** Zpráva S prefixem — pro chat zprávy. */
    public Component msg(String path, TagResolver... r) {
        String prefix = str("messages.prefix", "");
        String raw    = str("messages." + path, "<red>Missing: " + path);
        return parse(prefix + raw, r);
    }

    /** Surový string z messages — pro titly a placeholder hodnoty (BEZ prefixu). */
    public String raw(String path) {
        return str("messages." + path, "");
    }

    /** Parsuje string → Adventure Component. Podporuje legacy &x §x &#RRGGBB + MiniMessage. */
    public Component parse(String text, TagResolver... r) {
        if (text == null || text.isEmpty()) return Component.empty();
        return mm.deserialize(legacyToMm(text), r);
    }

    // ── title builder ─────────────────────────────────────────────────────────

    /**
     * Sestaví Title z configu.
     * @param titlePath   cesta k title textu   (bez "messages." prefixu)
     * @param subtitlePath cesta k subtitle textu (bez "messages." prefixu)
     * @param timingsPath cesta k timing sekci  (např. "titles.round_end.timings")
     * @param resolvers   placeholdery
     */
    public Title buildTitle(String titlePath, String subtitlePath,
                            String timingsPath, TagResolver... resolvers) {
        Component title    = parse(raw(titlePath),    resolvers);
        Component subtitle = parse(raw(subtitlePath), resolvers);

        long fadeIn  = getConfig().getLong(timingsPath + ".fade_in_ms",  400);
        long stay    = getConfig().getLong(timingsPath + ".stay_ms",     2500);
        long fadeOut = getConfig().getLong(timingsPath + ".fade_out_ms", 600);

        return Title.title(title, subtitle,
                Title.Times.times(
                        Duration.ofMillis(fadeIn),
                        Duration.ofMillis(stay),
                        Duration.ofMillis(fadeOut)));
    }

    // ── config gettery ────────────────────────────────────────────────────────

    public int  getDefaultRoundTime()    { return getConfig().getInt("game.default_round_time", 300); }
    public int  getDefaultRounds()       { return getConfig().getInt("game.default_rounds", 5); }
    public int  getCountdownStartAt()    { return getConfig().getInt("game.countdown_start_at", 15); }
    public long getRoundTransitionMs()   { return getConfig().getLong("game.round_transition_ms", 4000); }

    public int  getMaxWinners()          { return getConfig().getInt("end_game.max_winners", 3); }
    public long getWinnerDelayTicks()    { return getConfig().getLong("end_game.delay_between_ticks", 60L); }
    public boolean isWinnersAscending()  { return getConfig().getBoolean("end_game.ascending_order", true); }

    /** Items na root-level — sekce "items:", NE "lists.items". */
    public List<String> getItems()       { return getConfig().getStringList("items"); }
    /** Blocks na root-level — sekce "blocks:", NE "lists.blocks". */
    public List<String> getBlocks()      { return getConfig().getStringList("blocks"); }
    public List<String> getAbilities()   { return getConfig().getStringList("abilities.rewards"); }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String str(String path, String def) {
        String v = getConfig().getString(path, def);
        return v != null ? v : def;
    }

    private static String legacyToMm(String s) {
        s = s.replaceAll("&#([a-fA-F0-9]{6})", "<#$1>");
        final String[][] T = {
                {"&0","§0","<black>"},      {"&1","§1","<dark_blue>"},
                {"&2","§2","<dark_green>"}, {"&3","§3","<dark_aqua>"},
                {"&4","§4","<dark_red>"},   {"&5","§5","<dark_purple>"},
                {"&6","§6","<gold>"},       {"&7","§7","<gray>"},
                {"&8","§8","<dark_gray>"},  {"&9","§9","<blue>"},
                {"&a","§a","<green>"},      {"&b","§b","<aqua>"},
                {"&c","§c","<red>"},        {"&d","§d","<light_purple>"},
                {"&e","§e","<yellow>"},     {"&f","§f","<white>"},
                {"&l","§l","<bold>"},       {"&m","§m","<strikethrough>"},
                {"&n","§n","<underlined>"}, {"&o","§o","<italic>"},
                {"&r","§r","<reset>"}
        };
        for (String[] row : T) s = s.replace(row[0], row[2]).replace(row[1], row[2]);
        return s;
    }
}