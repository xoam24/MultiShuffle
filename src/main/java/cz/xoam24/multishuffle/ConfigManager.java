package cz.xoam24.multishuffle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final MultiShuffle plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ConfigManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    public void reloadConfig() {
        plugin.reloadConfig();
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    /** Vrátí zprávu s prefixem z configu jako Adventure Component. */
    public Component getMessage(String path, TagResolver... resolvers) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw    = getConfig().getString("messages." + path, "<red>Missing message: " + path);
        return parse(prefix + raw, resolvers);
    }

    /** Vrátí surový string zprávy z configu (bez prefixu, bez parsování). */
    public String getRaw(String path) {
        return getConfig().getString("messages." + path, "");
    }

    /**
     * Parsuje string s podporou legacy (&a, §a), hex (&#RRGGBB) i nativního MiniMessage.
     */
    public Component parse(String text, TagResolver... resolvers) {
        if (text == null || text.isEmpty()) return Component.empty();
        return mm.deserialize(legacyToMiniMessage(text), resolvers);
    }

    // ── gettery ──────────────────────────────────────────────────────────────

    public int getDefaultRoundTime()  { return getConfig().getInt("game.default_round_time", 300); }
    public int getAnnouncementDelay() { return getConfig().getInt("game.announcement_delay", 2); }
    public int getDefaultRounds()     { return getConfig().getInt("game.default_rounds", 5); }

    /** Items jsou na root-level v config.yml — sekce "items:", ne "lists.items". */
    public List<String> getItems()    { return getConfig().getStringList("items"); }
    /** Blocks jsou na root-level v config.yml — sekce "blocks:", ne "lists.blocks". */
    public List<String> getBlocks()   { return getConfig().getStringList("blocks"); }
    public List<String> getAbilities(){ return getConfig().getStringList("abilities.rewards"); }

    // ── privátní helper ──────────────────────────────────────────────────────

    private static String legacyToMiniMessage(String s) {
        // hex &#RRGGBB → <#RRGGBB>
        s = s.replaceAll("&#([a-fA-F0-9]{6})", "<#$1>");

        String[][] codes = {
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
        for (String[] row : codes) {
            s = s.replace(row[0], row[2]).replace(row[1], row[2]);
        }
        return s;
    }
}