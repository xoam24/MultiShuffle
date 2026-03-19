package cz.xoam24.multishuffle;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiShuffle extends JavaPlugin {

    private static MultiShuffle instance;
    private ConfigManager configManager;
    private SQLiteManager sqliteManager;
    private GameManager gameManager;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.sqliteManager = new SQLiteManager(this);
        this.sqliteManager.initializeDatabase();

        this.gameManager = new GameManager(this);
        this.scoreboardManager = new ScoreboardManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        CommandManager cmdManager = new CommandManager(this);
        if (getCommand("ms") != null) getCommand("ms").setExecutor(cmdManager);
        if (getCommand("is") != null) getCommand("is").setExecutor(cmdManager);
        if (getCommand("bs") != null) getCommand("bs").setExecutor(cmdManager);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI uspesne napojeno!");
        } else {
            getLogger().warning("PlaceholderAPI nebylo nalezeno! Placeholdery nebudou fungovat.");
        }

        getLogger().info("MultiShuffle byl uspesne zapnut! (Paper 1.21.11)");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.stopGame();
        if (sqliteManager != null) sqliteManager.closeConnection();
        getLogger().info("MultiShuffle byl vypnut.");
    }

    public static MultiShuffle getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public SQLiteManager getSqliteManager() { return sqliteManager; }
    public GameManager getGameManager() { return gameManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
}