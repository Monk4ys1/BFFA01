package infinode.org.example.bFFA01;

import infinode.org.example.bFFA01.commands.*;
import infinode.org.example.bFFA01.listeners.GameListener;
import infinode.org.example.bFFA01.listeners.ShopListener;
import infinode.org.example.bFFA01.managers.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class BFFA01 extends JavaPlugin {

    private KitManager kitManager;
    private MapManager mapManager;
    private DataManager dataManager;
    private KillstreakManager killstreakManager;
    private HologramManager hologramManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();

        dataManager = new DataManager(this);
        killstreakManager = new KillstreakManager();
        hologramManager = new HologramManager(this);
        kitManager = new KitManager(this);
        mapManager = new MapManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        PluginCommand bffaCmd = getCommand("bffa");
        if (bffaCmd != null) bffaCmd.setExecutor(new BuildFFAAdminCommand(this));

        PluginCommand statsCmd = getCommand("stats");
        if (statsCmd != null) statsCmd.setExecutor(new StatsCommand(this));

        PluginCommand shopCmd = getCommand("shop");
        if (shopCmd != null) shopCmd.setExecutor(new ShopCommand(this));

        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) kitCmd.setExecutor(new KitEditorCommand(this));

        PluginCommand saveKitCmd = getCommand("savekit");
        if (saveKitCmd != null) saveKitCmd.setExecutor(new SaveKitCommand(this));

        mapManager.startMapRotation();
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public KitManager getKitManager() { return kitManager; }
    public MapManager getMapManager() { return mapManager; }
    public DataManager getDataManager() { return dataManager; }
    public KillstreakManager getKillstreakManager() { return killstreakManager; }
    public HologramManager getHologramManager() { return hologramManager; }
}
