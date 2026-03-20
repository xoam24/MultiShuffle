package cz.xoam24.multishuffle;

import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLiteManager {

    private final MultiShuffle plugin;
    private Connection connection;

    public SQLiteManager(MultiShuffle plugin) { this.plugin = plugin; }

    public void initializeDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + new File(dataFolder, "database.db").getAbsolutePath());
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("[MultiShuffle] SQLite init error: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (PreparedStatement s = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS users_stats (" +
                        "uuid VARCHAR(36) PRIMARY KEY," +
                        "name VARCHAR(16)," +
                        "games_played INT DEFAULT 0," +
                        "games_won INT DEFAULT 0," +
                        "total_points INT DEFAULT 0" +
                        ");")) {
            s.execute();
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("[MultiShuffle] SQLite close error: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> savePlayerStatsAsync(UUID uuid, String name,
                                                        boolean winner, int points) {
        return CompletableFuture.runAsync(() -> {
            String sql =
                    "INSERT INTO users_stats (uuid, name, games_played, games_won, total_points) " +
                            "VALUES (?, ?, 1, ?, ?) " +
                            "ON CONFLICT(uuid) DO UPDATE SET " +
                            "name = ?, games_played = games_played + 1, " +
                            "games_won = games_won + ?, total_points = total_points + ?;";
            try (PreparedStatement s = connection.prepareStatement(sql)) {
                int w = winner ? 1 : 0;
                s.setString(1, uuid.toString());
                s.setString(2, name);
                s.setInt(3, w);
                s.setInt(4, points);
                s.setString(5, name);
                s.setInt(6, w);
                s.setInt(7, points);
                s.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[MultiShuffle] Chyba při ukládání statistik: " + e.getMessage());
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }
}