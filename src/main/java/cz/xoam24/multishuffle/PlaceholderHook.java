package cz.xoam24.multishuffle;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderHook extends PlaceholderExpansion {

    private final MultiShuffle plugin;

    public PlaceholderHook(MultiShuffle plugin) { this.plugin = plugin; }

    @Override
    public @NotNull String getIdentifier() { return "multishuffle"; }
    @Override
    public @NotNull String getAuthor() { return plugin.getDescription().getAuthors().get(0); }
    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return params.equals("time") ? "0" : "-";

        ConfigManager config = plugin.getConfigManager();

        switch (params.toLowerCase()) {
            case "time":
                int seconds = session.getRemainingSeconds();
                return String.format("%02d:%02d", seconds / 60, seconds % 60);
            case "item":
                if (session.hasFinished(player.getUniqueId())) {
                    return config.getRawMessage("placeholder_finished"); // Tahá custom string z configu
                }
                String target = session.getTarget(player.getUniqueId());
                return target != null ? target.replace("minecraft:", "").toUpperCase() : config.getRawMessage("placeholder_none");
            case "points":
                return String.valueOf(session.getPoints(player.getUniqueId()));
            case "round":
                return String.valueOf(session.getCurrentRound());
            case "maxround":
                return String.valueOf(session.getMaxRounds());
            case "ability":
                return config.getRawMessage("placeholder_ability_active");
            default:
                return null;
        }
    }
}