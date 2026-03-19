package cz.xoam24.multishuffle;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderHook extends PlaceholderExpansion {

    private final MultiShuffle plugin;

    public PlaceholderHook(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "multishuffle"; }
    @Override public @NotNull String getAuthor()     { return plugin.getDescription().getAuthors().get(0); }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GameSession session   = plugin.getGameManager().getCurrentSession();
        ConfigManager cfg     = plugin.getConfigManager();

        if (session == null) {
            return switch (params.toLowerCase()) {
                case "time"     -> "0";
                case "round"    -> "0";
                case "maxround" -> "0";
                case "points"   -> "0";
                default         -> "-";
            };
        }

        return switch (params.toLowerCase()) {
            case "time" -> {
                int s = session.getRemainingSeconds();
                yield String.format("%02d:%02d", s / 60, s % 60);
            }
            case "item" -> {
                if (session.hasFinished(player.getUniqueId())) {
                    yield cfg.getRaw("placeholder_finished");
                }
                String target = session.getTarget(player.getUniqueId());
                yield target != null
                        ? target.replace("minecraft:", "").replace("_", " ").toUpperCase()
                        : cfg.getRaw("placeholder_none");
            }
            case "points"   -> String.valueOf(session.getPoints(player.getUniqueId()));
            case "round"    -> String.valueOf(session.getCurrentRound());
            case "maxround" -> String.valueOf(session.getMaxRounds());
            case "ability"  -> cfg.getRaw("placeholder_ability_active");
            default         -> null;
        };
    }
}