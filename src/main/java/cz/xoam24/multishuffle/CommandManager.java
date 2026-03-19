package cz.xoam24.multishuffle;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final MultiShuffle plugin;

    public CommandManager(MultiShuffle plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("multishuffle.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return true;
        }

        if (label.equalsIgnoreCase("ms")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.getConfigManager().reloadConfig();
                sender.sendMessage(plugin.getConfigManager().getMessage("config_reloaded"));
                return true;
            }
            sender.sendMessage(plugin.getConfigManager().getMessage("help"));
            return true;
        }

        GameSession.Type type = label.equalsIgnoreCase("is") ? GameSession.Type.ITEM : GameSession.Type.BLOCK;

        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("command_usage", Placeholder.parsed("command", label)));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                if (plugin.getGameManager().getCurrentSession() != null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("game_already_running"));
                    return true;
                }
                GameSession.Mode mode = GameSession.Mode.RANDOM;
                plugin.getGameManager().startGame(type, mode);
                break;
            case "stop":
                if (plugin.getGameManager().getCurrentSession() == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no_game_running"));
                    return true;
                }
                plugin.getGameManager().stopGame();
                break;
            case "skip":
                if (plugin.getGameManager().getCurrentSession() == null) {
                    sender.sendMessage(plugin.getConfigManager().getMessage("no_game_running"));
                    return true;
                }
                plugin.getGameManager().skipRound();
                // Zpráva "round_skipped_success" byla odstraněna.
                break;
            default:
                sender.sendMessage(plugin.getConfigManager().getMessage("invalid_argument"));
                break;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (alias.equalsIgnoreCase("ms") && args.length == 1) {
            completions.add("reload"); completions.add("help");
        } else if ((alias.equalsIgnoreCase("is") || alias.equalsIgnoreCase("bs")) && args.length == 1) {
            completions.add("start"); completions.add("stop"); completions.add("skip");
        }
        return completions;
    }
}