package infinode.org.example.bFFA01;

import infinode.org.example.bFFA01.commands.BFFACommand;
import infinode.org.example.bFFA01.commands.BuildCommand;
import infinode.org.example.bFFA01.commands.KitCommand;
import infinode.org.example.bFFA01.commands.SaveKitCommand;
import infinode.org.example.bFFA01.commands.ShopCommand;
import infinode.org.example.bFFA01.commands.StatsCommand;
import infinode.org.example.bFFA01.compat.ActionBar;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.compat.ServerVersion;
import infinode.org.example.bFFA01.config.ConfigMigrator;
import infinode.org.example.bFFA01.config.Messages;
import infinode.org.example.bFFA01.config.Settings;
import infinode.org.example.bFFA01.data.DataManager;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.BlockManager;
import infinode.org.example.bFFA01.game.BuildModeService;
import infinode.org.example.bFFA01.game.CombatManager;
import infinode.org.example.bFFA01.game.KillstreakManager;
import infinode.org.example.bFFA01.game.KitManager;
import infinode.org.example.bFFA01.game.MapManager;
import infinode.org.example.bFFA01.listeners.CombatListener;
import infinode.org.example.bFFA01.listeners.PlayerListener;
import infinode.org.example.bFFA01.listeners.ProtectionListener;
import infinode.org.example.bFFA01.listeners.SpecialItemListener;
import infinode.org.example.bFFA01.shop.ShopRegistry;
import infinode.org.example.bFFA01.shop.SpecialItemState;
import infinode.org.example.bFFA01.ui.GuiListener;
import infinode.org.example.bFFA01.ui.HologramService;
import infinode.org.example.bFFA01.ui.ScoreboardService;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BuildFFA.
 *
 * <p>The plugin builds every service here and hands the instance around, so
 * there is exactly one owner for each piece of state and nothing reaches for a
 * static singleton.
 */
public final class BFFA01 extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private ActionBar actionBar;

    private DataManager dataManager;
    private MapManager mapManager;
    private BlockManager blockManager;
    private CombatManager combatManager;
    private KitManager kitManager;
    private KillstreakManager killstreakManager;
    private BuildModeService buildMode;

    private ShopRegistry shopRegistry;
    private SpecialItemState specialItemState;

    private ScoreboardService scoreboardService;
    private HologramService hologramService;

    @Override
    public void onEnable() {
        if (!ServerVersion.isSupported()) {
            getLogger().severe("BuildFFA supports Minecraft " + ServerVersion.MIN_MINOR + "."
                    + ServerVersion.MIN_PATCH + " and newer; this server reports "
                    + ServerVersion.raw() + ". Continuing, but expect problems.");
        }

        settings = new Settings();
        messages = new Messages();
        actionBar = new ActionBar(this);
        buildMode = new BuildModeService();
        specialItemState = new SpecialItemState();

        saveDefaultConfig();
        dataManager = new DataManager(this);
        mapManager = new MapManager(this);
        blockManager = new BlockManager(this);
        combatManager = new CombatManager(this);
        kitManager = new KitManager(this);
        killstreakManager = new KillstreakManager(this);
        shopRegistry = new ShopRegistry(this);

        // Move 4.x settings and arenas into the 5.x layout before anything
        // reads them, then top the file up with keys added by this release.
        ConfigMigrator.run(this, getConfig(), this::saveConfig, mapManager.storage());
        getConfig().options().copyDefaults(true);
        saveConfig();

        scoreboardService = new ScoreboardService(this);
        hologramService = new HologramService(this);

        loadConfiguration();
        registerListeners();
        registerCommands();
        startServices();

        getLogger().info("BuildFFA " + getDescription().getVersion() + " enabled on "
                + ServerVersion.shortVersion() + ".");
    }

    @Override
    public void onDisable() {
        if (blockManager != null) {
            blockManager.stop();
            blockManager.clearAll();
        }
        if (hologramService != null) {
            hologramService.stop();
        }
        if (scoreboardService != null) {
            scoreboardService.stop();
        }
        if (combatManager != null) {
            combatManager.stop();
        }
        if (mapManager != null) {
            mapManager.stop();
        }
        if (dataManager != null) {
            dataManager.shutdown();
        }
    }

    /** Re-reads every configuration file and restarts the timers. */
    public void reloadEverything() {
        reloadConfig();
        loadConfiguration();
        scoreboardService.reload();
        mapManager.loadArenas();

        blockManager.start();
        combatManager.start();
        scoreboardService.start();
        hologramService.start();
        mapManager.rebuildBossBar();
        dataManager.startAutosave(settings.autosaveSeconds());
        scoreboardService.refreshAll();
    }

    private void loadConfiguration() {
        settings.reload(getConfig());
        messages.reload(getConfig());
        kitManager.reload();
        killstreakManager.reload();
        shopRegistry.reload();
    }

    private void registerListeners() {
        register(new GuiListener());
        register(new PlayerListener(this));
        register(new CombatListener(this));
        register(new ProtectionListener(this));
        register(new SpecialItemListener(this));
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerCommands() {
        register("bffa", new BFFACommand(this));
        register("build", new BuildCommand(this));
        register("shop", new ShopCommand(this));
        register("kit", new KitCommand(this));
        register("savekit", new SaveKitCommand(this));
        register("stats", new StatsCommand(this));
    }

    private void register(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void startServices() {
        blockManager.start();
        combatManager.start();
        mapManager.start();
        scoreboardService.start();
        hologramService.start();
        dataManager.startAutosave(settings.autosaveSeconds());
        // Players are already online after a /reload of the server.
        for (Player player : getServer().getOnlinePlayers()) {
            scoreboardService.setup(player);
            mapManager.showBossBar(player);
        }
    }

    /**
     * Applies everything a kill grants: statistics, coins, killstreak, health
     * and the ammunition top-up. Shared by the normal death path and the
     * combat-log path so the two can never drift apart.
     */
    public void rewardKill(Player killer, Player victim) {
        PlayerData data = dataManager.get(killer);
        data.addKill();
        killstreakManager.addKill(killer);

        int streak = killstreakManager.streak(killer);
        int coins = settings.coinsPerKill() + settings.coinsPerKillstreak() * Math.max(0, streak - 1);
        data.addCoins(coins);
        dataManager.markDirty();

        if (settings.healOnKill()) {
            Compat.heal(killer);
        }
        kitManager.rewardAfterKill(killer);
        messages.send(killer, "kill-reward", "coins", coins, "streak", streak, "victim", victim.getName());
        Compat.playSound(killer, "entity.experience_orb.pickup", 1.0F, 1.4F);
    }

    // ------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public ActionBar actionBar() {
        return actionBar;
    }

    public DataManager dataManager() {
        return dataManager;
    }

    public MapManager mapManager() {
        return mapManager;
    }

    public BlockManager blockManager() {
        return blockManager;
    }

    public CombatManager combatManager() {
        return combatManager;
    }

    public KitManager kitManager() {
        return kitManager;
    }

    public KillstreakManager killstreakManager() {
        return killstreakManager;
    }

    public BuildModeService buildMode() {
        return buildMode;
    }

    public ShopRegistry shopRegistry() {
        return shopRegistry;
    }

    public SpecialItemState specialItemState() {
        return specialItemState;
    }

    public ScoreboardService scoreboardService() {
        return scoreboardService;
    }

    public HologramService hologramService() {
        return hologramService;
    }
}
