package cz.xoam24.multishuffle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final MultiShuffle plugin;

    public CommandManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── command dispatch ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("multishuffle.admin")) {
            sender.sendMessage(plugin.getConfigManager().msg("no_permission"));
            return true;
        }

        String lbl = label.toLowerCase();

        // /ms — admin příkazy
        if (lbl.equals("ms")) {
            return handleMs(sender, args);
        }

        // /is nebo /bs
        GameSession.Type type = lbl.equals("is") ? GameSession.Type.ITEM : GameSession.Type.BLOCK;
        return handleShuffle(sender, type, label, args);
    }

    // ── /ms ───────────────────────────────────────────────────────────────────

    private boolean handleMs(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().msg("help"));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.getConfigManager().reload();
                sender.sendMessage(plugin.getConfigManager().msg("config_reloaded"));
            }

            // /ms points set <player|@a> <value>
            // /ms points list
            case "points" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().msg("command_usage",
                            Placeholder.parsed("command", "ms points <set|list>")));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "set"  -> handlePointsSet(sender, args);
                    case "list" -> handlePointsList(sender);
                    default -> sender.sendMessage(plugin.getConfigManager().msg("invalid_argument"));
                }
            }

            default -> sender.sendMessage(plugin.getConfigManager().msg("invalid_argument"));
        }
        return true;
    }

    /** /ms points set <player|@a> <value> */
    private void handlePointsSet(CommandSender sender, String[] args) {
        // args: [points, set, <target>, <value>]
        if (args.length < 4) {
            sender.sendMessage(plugin.getConfigManager().msg("command_usage",
                    Placeholder.parsed("command", "ms points set <player|@a> <hodnota>")));
            return;
        }

        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) {
            sender.sendMessage(plugin.getConfigManager().msg("no_game_running"));
            return;
        }

        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getConfigManager().msg("invalid_number",
                    Placeholder.parsed("input", args[3])));
            return;
        }

        String target = args[2];

        if (target.equals("@a")) {
            plugin.getGameManager().setPointsAll(value);
            sender.sendMessage(plugin.getConfigManager().msg("points_set_all",
                    Placeholder.parsed("value", String.valueOf(value))));
        } else {
            Player p = Bukkit.getPlayerExact(target);
            if (p == null) {
                sender.sendMessage(plugin.getConfigManager().msg("player_not_found",
                        Placeholder.parsed("player", target)));
                return;
            }
            plugin.getGameManager().setPoints(p.getUniqueId(), value);
            sender.sendMessage(plugin.getConfigManager().msg("points_set",
                    Placeholder.parsed("player", p.getName()),
                    Placeholder.parsed("value",  String.valueOf(value))));
        }
    }

    /** /ms points list — vypíše body všech online hráčů */
    private void handlePointsList(CommandSender sender) {
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) {
            sender.sendMessage(plugin.getConfigManager().msg("no_game_running"));
            return;
        }

        sender.sendMessage(plugin.getConfigManager().msg("points_list_header"));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort((a, b) -> Integer.compare(
                session.getPoints(b.getUniqueId()),
                session.getPoints(a.getUniqueId())));

        for (Player p : players) {
            sender.sendMessage(plugin.getConfigManager().msg("points_list_entry",
                    Placeholder.parsed("player", p.getName()),
                    Placeholder.parsed("points", String.valueOf(session.getPoints(p.getUniqueId())))));
        }
    }

    // ── /is & /bs ─────────────────────────────────────────────────────────────

    private boolean handleShuffle(CommandSender sender, GameSession.Type type,
                                  String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().msg("command_usage",
                    Placeholder.parsed("command", label + " <start|stop|skip|mode>")));
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "start" -> {
                if (plugin.getGameManager().getCurrentSession() != null) {
                    sender.sendMessage(plugin.getConfigManager().msg("game_already_running"));
                    return true;
                }
                plugin.getGameManager().startGame(type);
            }

            case "stop" -> {
                if (plugin.getGameManager().getCurrentSession() == null) {
                    sender.sendMessage(plugin.getConfigManager().msg("no_game_running"));
                    return true;
                }
                plugin.getGameManager().stopGame();
            }

            case "skip" -> {
                if (plugin.getGameManager().getCurrentSession() == null) {
                    sender.sendMessage(plugin.getConfigManager().msg("no_game_running"));
                    return true;
                }
                plugin.getGameManager().skipRound();
            }

            // /is mode <same|random>
            // /bs mode <same|random>
            case "mode" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.getConfigManager().msg("command_usage",
                            Placeholder.parsed("command", label + " mode <same|random>")));
                    return true;
                }
                GameSession.Mode mode = switch (args[1].toLowerCase()) {
                    case "same"   -> GameSession.Mode.SAME;
                    case "random" -> GameSession.Mode.RANDOM;
                    default -> null;
                };
                if (mode == null) {
                    sender.sendMessage(plugin.getConfigManager().msg("invalid_argument"));
                    return true;
                }
                plugin.getGameManager().setMode(type, mode);
                sender.sendMessage(plugin.getConfigManager().msg("mode_set",
                        Placeholder.parsed("type", type.name().toLowerCase()),
                        Placeholder.parsed("mode", mode.name().toLowerCase())));
            }

            // /is settings time <seconds>
            // /is settings round <count>
            // /bs settings time <seconds>
            // /bs settings round <count>
            case "settings" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.getConfigManager().msg("command_usage",
                            Placeholder.parsed("command", label + " settings <time|round> <hodnota>")));
                    return true;
                }

                int newValue;
                try {
                    newValue = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.getConfigManager().msg("invalid_number",
                            Placeholder.parsed("input", args[2])));
                    return true;
                }

                switch (args[1].toLowerCase()) {

                    case "time" -> {
                        if (newValue < 10) {
                            sender.sendMessage(plugin.getConfigManager().msg("settings_time_min"));
                            return true;
                        }
                        int oldValue = plugin.getGameManager().setRoundTime(newValue);
                        GameSession s = plugin.getGameManager().getCurrentSession();
                        String when = (s != null)
                                ? plugin.getConfigManager().raw("settings_applies_next_round")
                                : plugin.getConfigManager().raw("settings_applies_next_game");
                        Bukkit.broadcast(plugin.getConfigManager().msg("settings_time_changed",
                                Placeholder.parsed("old",   String.valueOf(oldValue)),
                                Placeholder.parsed("new",   String.valueOf(newValue)),
                                Placeholder.parsed("when",  when),
                                Placeholder.parsed("sender", sender.getName())));
                    }

                    case "round" -> {
                        if (newValue < 1) {
                            sender.sendMessage(plugin.getConfigManager().msg("settings_round_min"));
                            return true;
                        }
                        int oldValue = plugin.getGameManager().setRounds(newValue);
                        GameSession s = plugin.getGameManager().getCurrentSession();
                        String when = (s != null)
                                ? plugin.getConfigManager().raw("settings_applies_now")
                                : plugin.getConfigManager().raw("settings_applies_next_game");
                        Bukkit.broadcast(plugin.getConfigManager().msg("settings_round_changed",
                                Placeholder.parsed("old",    String.valueOf(oldValue)),
                                Placeholder.parsed("new",    String.valueOf(newValue)),
                                Placeholder.parsed("when",   when),
                                Placeholder.parsed("sender", sender.getName())));
                    }

                    default -> sender.sendMessage(plugin.getConfigManager().msg("invalid_argument"));
                }
            }

            default -> sender.sendMessage(plugin.getConfigManager().msg("invalid_argument"));
        }
        return true;
    }

    // ── tab complete ──────────────────────────────────────────────────────────

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                                @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        String lbl = alias.toLowerCase();

        if (lbl.equals("ms")) {
            if (args.length == 1) out.addAll(Arrays.asList("reload", "points"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("points"))
                out.addAll(Arrays.asList("set", "list"));
            else if (args.length == 3 && args[0].equalsIgnoreCase("points")
                    && args[1].equalsIgnoreCase("set")) {
                out.add("@a");
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 4 && args[0].equalsIgnoreCase("points")
                    && args[1].equalsIgnoreCase("set")) {
                out.addAll(Arrays.asList("0", "1", "5", "10"));
            }
        } else if (lbl.equals("is") || lbl.equals("bs")) {
            if (args.length == 1)
                out.addAll(Arrays.asList("start", "stop", "skip", "mode", "settings"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("mode"))
                out.addAll(Arrays.asList("same", "random"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("settings"))
                out.addAll(Arrays.asList("time", "round"));
            else if (args.length == 3 && args[0].equalsIgnoreCase("settings")) {
                switch (args[1].toLowerCase()) {
                    case "time"  -> out.addAll(Arrays.asList("30", "60", "120", "180", "300", "600"));
                    case "round" -> out.addAll(Arrays.asList("1", "3", "5", "10", "15", "20"));
                }
            }
        }

        // Filtruj podle toho co uživatel zatím napsal
        String partial = args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(partial));
        return out;
    }
}