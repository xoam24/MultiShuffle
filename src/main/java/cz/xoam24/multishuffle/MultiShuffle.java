package cz.xoam24.multishuffle;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiShuffle extends JavaPlugin {

    private static MultiShuffle instance;

    private ConfigManager    configManager;
    private SQLiteManager    sqliteManager;
    private SoundManager     soundManager;
    private GameManager      gameManager;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        configManager    = new ConfigManager(this);
        sqliteManager    = new SQLiteManager(this);
        sqliteManager.initializeDatabase();
        soundManager     = new SoundManager(this);
        gameManager      = new GameManager(this);
        scoreboardManager= new ScoreboardManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        CommandManager cmd = new CommandManager(this);
        registerCmd("ms", cmd);
        registerCmd("is", cmd);
        registerCmd("bs", cmd);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info("PlaceholderAPI napojeno.");
        } else {
            getLogger().warning("PlaceholderAPI nenalezeno — placeholdery nebudou fungovat.");
        }

        getLogger().info("MultiShuffle " + getDescription().getVersion() + " zapnut.");
    }

    @Override
    public void onDisable() {
        if (gameManager  != null) gameManager.stopGame();
        if (sqliteManager!= null) sqliteManager.closeConnection();
        getLogger().info("MultiShuffle vypnut.");
    }

    private void registerCmd(String name, CommandManager handler) {
        var cmd = getCommand(name);
        if (cmd != null) { cmd.setExecutor(handler); cmd.setTabCompleter(handler); }
    }

    public static MultiShuffle getInstance()        { return instance; }
    public ConfigManager    getConfigManager()      { return configManager; }
    public SQLiteManager    getSqliteManager()      { return sqliteManager; }
    public SoundManager     getSoundManager()       { return soundManager; }
    public GameManager      getGameManager()        { return gameManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
}