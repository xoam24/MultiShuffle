package cz.xoam24.multishuffle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final MultiShuffle plugin;
    private final MiniMessage miniMessage;

    public ConfigManager(MultiShuffle plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void reloadConfig() { plugin.reloadConfig(); }
    public FileConfiguration getConfig() { return plugin.getConfig(); }

    public Component getMessage(String path, TagResolver... placeholders) {
        String prefix = getConfig().getString("messages.prefix", "");
        String rawMessage = getConfig().getString("messages." + path, "<red>Zpráva nenalezena: " + path + "</red>");
        return parseComponent(prefix + rawMessage, placeholders);
    }

    // Extrémně robustní parser, který převádí legacy (&a) a hex (&#ff0000) na nativní MiniMessage tagy
    public Component parseComponent(String text, TagResolver... resolvers) {
        if (text == null) return Component.empty();

        // Klasické znaky a paragrafy
        text = text.replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");

        text = text.replace("§0", "<black>").replace("§1", "<dark_blue>").replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>").replace("§4", "<dark_red>").replace("§5", "<dark_purple>")
                .replace("§6", "<gold>").replace("§7", "<gray>").replace("§8", "<dark_gray>")
                .replace("§9", "<blue>").replace("§a", "<green>").replace("§b", "<aqua>")
                .replace("§c", "<red>").replace("§d", "<light_purple>").replace("§e", "<yellow>")
                .replace("§f", "<white>").replace("§l", "<bold>").replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>").replace("§o", "<italic>").replace("§r", "<reset>");

        // HEX barvy (&#RRGGBB na <#RRGGBB>)
        text = text.replaceAll("&#([a-fA-F0-9]{6})", "<#$1>");

        return miniMessage.deserialize(text, resolvers);
    }

    public String getRawMessage(String path) { return getConfig().getString("messages." + path, ""); }
    public int getDefaultRoundTime() { return getConfig().getInt("game.default_round_time", 300); }
    public int getAnnouncementDelay() { return getConfig().getInt("game.announcement_delay", 5); }
    public int getDefaultRounds() { return getConfig().getInt("game.default_rounds", 5); }
    public List<String> getItems() { return getConfig().getStringList("lists.items"); }
    public List<String> getBlocks() { return getConfig().getStringList("lists.blocks"); }
    public List<String> getAbilities() { return getConfig().getStringList("abilities.rewards"); }
}