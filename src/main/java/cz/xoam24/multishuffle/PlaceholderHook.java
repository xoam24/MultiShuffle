package cz.xoam24.multishuffle;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dostupné placeholdery:
 * %multishuffle_time%      — zbývající čas ve formátu MM:SS
 * %multishuffle_item%      — aktuální target hráče (nebo "Splněno" / "N/A")
 * %multishuffle_points%    — body hráče v aktuální hře
 * %multishuffle_round%     — aktuální kolo
 * %multishuffle_maxround%  — maximální počet kol
 * %multishuffle_mode%      — aktuální mód (same / random)
 * %multishuffle_type%      — typ hry (item / block)
 */
public class PlaceholderHook extends PlaceholderExpansion {

    private final MultiShuffle plugin;

    public PlaceholderHook(MultiShuffle plugin) { this.plugin = plugin; }

    @Override public @NotNull String getIdentifier() { return "multishuffle"; }
    @Override public @NotNull String getAuthor()     { return plugin.getDescription().getAuthors().get(0); }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist()               { return true; }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        GameSession   s   = plugin.getGameManager().getCurrentSession();
        ConfigManager cfg = plugin.getConfigManager();

        if (s == null) {
            return switch (params.toLowerCase()) {
                case "time"     -> "00:00";
                case "points"   -> "0";
                case "round"    -> "0";
                case "maxround" -> "0";
                case "mode"     -> "-";
                case "type"     -> "-";
                default         -> "-";
            };
        }

        return switch (params.toLowerCase()) {

            case "time" -> {
                int sec = s.getRemainingSeconds();
                yield String.format("%02d:%02d", sec / 60, sec % 60);
            }

            case "item" -> {
                if (s.hasFinished(player.getUniqueId()))
                    yield cfg.raw("placeholder_finished");
                String t = s.getTarget(player.getUniqueId());
                if (t == null) yield cfg.raw("placeholder_none");
                yield t.replace("minecraft:", "").replace("_", " ").toUpperCase();
            }

            case "points"   -> String.valueOf(s.getPoints(player.getUniqueId()));
            case "round"    -> String.valueOf(s.getCurrentRound());
            case "maxround" -> String.valueOf(s.getMaxRounds());
            case "mode"     -> s.getMode().name().toLowerCase();
            case "type"     -> s.getType().name().toLowerCase();

            default -> null;
        };
    }
}