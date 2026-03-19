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

            File dbFile = new File(dataFolder, "database.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Chyba při inicializaci SQLite: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS users_stats (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16)," +
                "games_played INT DEFAULT 0," +
                "games_won INT DEFAULT 0," +
                "total_points INT DEFAULT 0" +
                ");";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Chyba pri uzavirani databaze: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> savePlayerStatsAsync(UUID uuid, String name, boolean isWinner, int earnedPoints) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO users_stats (uuid, name, games_played, games_won, total_points) " +
                    "VALUES (?, ?, 1, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "name = ?, games_played = games_played + 1, " +
                    "games_won = games_won + ?, total_points = total_points + ?;";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, name);
                stmt.setInt(3, isWinner ? 1 : 0);
                stmt.setInt(4, earnedPoints);
                stmt.setString(5, name);
                stmt.setInt(6, isWinner ? 1 : 0);
                stmt.setInt(7, earnedPoints);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Chyba pri ukladani statistik do databaze pro hrace " + name);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task));
    }
}