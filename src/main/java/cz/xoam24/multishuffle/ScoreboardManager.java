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
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(MultiShuffle plugin) {
        this.plugin = plugin;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public void showScoreboards() {
        if (!isEnabled()) return;
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) createBoardForPlayer(p, s);
    }

    public void hideScoreboards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    // ── update — volá se každou sekundu + při události ────────────────────────

    public void updateScoreboards() {
        GameSession s = plugin.getGameManager().getCurrentSession();
        if (s == null || !isEnabled()) return;

        List<String> lines = getLines(s);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Scoreboard board = boards.get(p.getUniqueId());
            if (board == null) {
                createBoardForPlayer(p, s);
                board = boards.get(p.getUniqueId());
                if (board == null) continue;
            }

            Objective obj = board.getObjective("ms");
            if (obj == null) continue;

            for (int i = 0; i < lines.size(); i++) {
                // PlaceholderAPI per-player nahrazení
                String raw  = PlaceholderAPI.setPlaceholders(p, lines.get(i));
                Component c = plugin.getConfigManager().parse(raw);

                String entry = entryFor(i);

                Team team = board.getTeam("l" + i);
                if (team == null) {
                    team = board.registerNewTeam("l" + i);
                    team.addEntry(entry);
                    obj.getScore(entry).setScore(lines.size() - i);
                }
                team.prefix(c);
            }
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    public void createBoardForPlayer(Player p, GameSession s) {
        String titlePath = s.getType() == GameSession.Type.ITEM
                ? "scoreboards.item_shuffle.title"
                : "scoreboards.block_shuffle.title";

        Component title = plugin.getConfigManager().parse(
                plugin.getConfigManager().getConfig().getString(titlePath, "MultiShuffle"));

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective  obj   = board.registerNewObjective("ms", Criteria.DUMMY, title);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        p.setScoreboard(board);
        boards.put(p.getUniqueId(), board);
    }

    // ── private ───────────────────────────────────────────────────────────────

    private boolean isEnabled() {
        return plugin.getConfigManager().getConfig().getBoolean("scoreboards.enabled", true);
    }

    private List<String> getLines(GameSession s) {
        String path = s.getType() == GameSession.Type.ITEM
                ? "scoreboards.item_shuffle.lines"
                : "scoreboards.block_shuffle.lines";
        return plugin.getConfigManager().getConfig().getStringList(path);
    }

    /**
     * Unikátní neviditelný entry identifikátor pro každý řádek.
     * Kombinuje §-kódy — maximálně 256 řádků.
     */
    private static String entryFor(int i) {
        return "\u00A7" + Integer.toHexString(i % 16)
                + "\u00A7" + Integer.toHexString((i / 16) % 16)
                + "\u00A7r";
    }
}