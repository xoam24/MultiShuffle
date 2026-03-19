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

    public ScoreboardManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public void showScoreboards() {
        if (!plugin.getConfigManager().getConfig().getBoolean("scoreboards.enabled", true)) return;

        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            createBoardForPlayer(p, session);
        }
    }

    public void hideScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        scoreboards.clear();
    }

    // ── update ────────────────────────────────────────────────────────────────

    public void updateScoreboards() {
        GameSession session = plugin.getGameManager().getCurrentSession();
        if (session == null) return;

        String linesPath = session.getType() == GameSession.Type.ITEM
                ? "scoreboards.item_shuffle.lines"
                : "scoreboards.block_shuffle.lines";

        List<String> lines = plugin.getConfigManager().getConfig().getStringList(linesPath);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = scoreboards.get(p.getUniqueId());

            // Hráč se připojil mid-game — vytvoříme mu board
            if (board == null) {
                createBoardForPlayer(p, session);
                board = scoreboards.get(p.getUniqueId());
                if (board == null) continue;
            }

            Objective obj = board.getObjective("ms_board");
            if (obj == null) continue;

            for (int i = 0; i < lines.size(); i++) {
                String raw      = PlaceholderAPI.setPlaceholders(p, lines.get(i));
                Component comp  = plugin.getConfigManager().parse(raw);

                // Unikátní neviditelný entry (kombinace §-kódů)
                String entry = "\u00A7" + Integer.toHexString(i % 16)
                        + "\u00A7" + Integer.toHexString((i / 16) % 16) + "\u00A7r";

                Team team = board.getTeam("line_" + i);
                if (team == null) {
                    team = board.registerNewTeam("line_" + i);
                    team.addEntry(entry);
                }
                team.prefix(comp);
                obj.getScore(entry).setScore(lines.size() - i);
            }
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /** Vytvoří nový scoreboard a přiřadí ho hráči. */
    public void createBoardForPlayer(Player p, GameSession session) {
        String titlePath = session.getType() == GameSession.Type.ITEM
                ? "scoreboards.item_shuffle.title"
                : "scoreboards.block_shuffle.title";

        Component title = plugin.getConfigManager().parse(
                plugin.getConfigManager().getConfig().getString(titlePath, "MultiShuffle"));

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj    = board.registerNewObjective("ms_board", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        p.setScoreboard(board);
        scoreboards.put(p.getUniqueId(), board);
    }
}