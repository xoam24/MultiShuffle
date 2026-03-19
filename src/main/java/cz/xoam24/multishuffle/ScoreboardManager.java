package cz.xoam24.multishuffle;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {

    private final MultiShuffle plugin;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    public ScoreboardManager(MultiShuffle plugin) { this.plugin = plugin; }

    public void showScoreboards() {
        if (!plugin.getConfigManager().getConfig().getBoolean("scoreboards.enabled", true)) return;

        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return;

        String path = session.getType() == GameSession.Type.ITEM ? "scoreboards.item_shuffle" : "scoreboards.block_shuffle";
        Component title = plugin.getConfigManager().parseComponent(plugin.getConfigManager().getConfig().getString(path + ".title", "MultiShuffle"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective("ms_board", Criteria.DUMMY, title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            p.setScoreboard(board);
            scoreboards.put(p.getUniqueId(), board);
        }
    }

    public void hideScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        scoreboards.clear();
    }

    public void updateScoreboards() {
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return;

        String path = session.getType() == GameSession.Type.ITEM ? "scoreboards.item_shuffle.lines" : "scoreboards.block_shuffle.lines";
        List<String> lines = plugin.getConfigManager().getConfig().getStringList(path);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = scoreboards.get(p.getUniqueId());
            if (board == null) continue;

            Objective obj = board.getObjective("ms_board");
            if (obj == null) continue;

            for (int i = 0; i < lines.size(); i++) {
                int score = lines.size() - i;
                String line = lines.get(i);
                String papiLine = PlaceholderAPI.setPlaceholders(p, line);
                Component comp = plugin.getConfigManager().parseComponent(papiLine);

                // Unikátní a neviditelný identifikátor pro řádek (barevné kódy)
                String entryName = "§" + Integer.toHexString(i % 16) + "§" + Integer.toHexString((i / 16) % 16) + "§r";

                Team team = board.getTeam("line_" + i);
                if (team == null) {
                    team = board.registerNewTeam("line_" + i);
                    team.addEntry(entryName);
                }
                team.prefix(comp); // Zde vkládáme plný komponent (s Hex barvami atd.)

                org.bukkit.scoreboard.Score scoreObj = obj.getScore(entryName);
                scoreObj.setScore(score);

            }
        }
    }
}