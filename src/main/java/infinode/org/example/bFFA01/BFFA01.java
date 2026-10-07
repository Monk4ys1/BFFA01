package infinode.org.example.bFFA01;

import infinode.org.example.bFFA01.commands.*;
import infinode.org.example.bFFA01.listeners.GameListener;
import infinode.org.example.bFFA01.managers.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BFFA01 extends JavaPlugin {

    private KitManager kitManager;
    private MapManager mapManager;
    private DataManager dataManager;
    private KillstreakManager killstreakManager;
    private ScoreboardManager scoreboardManager;
    private GameListener gameListener;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        dataManager = new DataManager(this);
        killstreakManager = new KillstreakManager();
        kitManager = new KitManager(this);
        
        gameListener = new GameListener(this);
        mapManager = new MapManager(this); // MapManager needs GameListener initialized first for clearAllBlocks
        scoreboardManager = new ScoreboardManager(this);

        getServer().getPluginManager().registerEvents(gameListener, this);

        PluginCommand bffaCmd = getCommand("bffa");
        if (bffaCmd != null) bffaCmd.setExecutor(new BuildFFAAdminCommand(this));

        PluginCommand statsCmd = getCommand("stats");
        if (statsCmd != null) statsCmd.setExecutor(new StatsCommand(this));

        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) kitCmd.setExecutor(new KitEditorCommand(this));

        PluginCommand saveKitCmd = getCommand("savekit");
        if (saveKitCmd != null) saveKitCmd.setExecutor(new SaveKitCommand(this));

        mapManager.startMapRotation();
    }

    @Override
    public void onDisable() {
        try {
            if (gameListener != null) {
                gameListener.clearAllBlocks();
            }
        } finally {
            if (dataManager != null) {
                dataManager.flush();
            }
        }
    }

    public KitManager getKitManager() { return kitManager; }
    public MapManager getMapManager() { return mapManager; }
    public DataManager getDataManager() { return dataManager; }
    public KillstreakManager getKillstreakManager() { return killstreakManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public GameListener getGameListener() { return gameListener; }
}
