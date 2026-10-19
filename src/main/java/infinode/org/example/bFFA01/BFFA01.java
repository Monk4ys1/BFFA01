package infinode.org.example.bFFA01;

import infinode.org.example.bFFA01.commands.BrigadierHook;
import infinode.org.example.bFFA01.commands.BuildFFAAdminCommand;
import infinode.org.example.bFFA01.commands.KitEditorCommand;
import infinode.org.example.bFFA01.commands.SaveKitCommand;
import infinode.org.example.bFFA01.commands.ShopCommand;
import infinode.org.example.bFFA01.commands.StatsCommand;
import infinode.org.example.bFFA01.compat.ServerRuntime;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.config.ConfigMigrator;
import infinode.org.example.bFFA01.hooks.IntegrationBus;
import infinode.org.example.bFFA01.hooks.Integrations;
import infinode.org.example.bFFA01.hooks.MetricsPublisher;
import infinode.org.example.bFFA01.hooks.PlaceholderResolver;
import infinode.org.example.bFFA01.hooks.WebhookPublisher;
import infinode.org.example.bFFA01.listeners.GameListener;
import infinode.org.example.bFFA01.listeners.IntegrationListener;
import infinode.org.example.bFFA01.listeners.ShopListener;
import infinode.org.example.bFFA01.managers.DataManager;
import infinode.org.example.bFFA01.managers.HologramManager;
import infinode.org.example.bFFA01.managers.KillstreakManager;
import infinode.org.example.bFFA01.managers.KitManager;
import infinode.org.example.bFFA01.managers.MapManager;
import infinode.org.example.bFFA01.managers.ScoreboardManager;
import infinode.org.example.bFFA01.service.ServiceRegistry;
import infinode.org.example.bFFA01.text.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class BFFA01 extends JavaPlugin {

    private ServerRuntime runtime;
    private ServerScheduler scheduler;
    private Messages messages;
    private ServiceRegistry services;
    private IntegrationBus integrations;
    private KitManager kitManager;
    private MapManager mapManager;
    private DataManager dataManager;
    private KillstreakManager killstreakManager;
    private ScoreboardManager scoreboardManager;
    private HologramManager hologramManager;
    private GameListener gameListener;

    @Override
    public void onLoad() {
        BrigadierHook.install(this, Map.of(
                "bffa", List.of("setmap", "swapmap", "addcoins", "removecoins", "resetstats", "reload")
        ));
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrate(new File(getDataFolder(), "config.yml"), getLogger());
        reloadConfig();
        saveLanguage("en");
        saveLanguage("de");

        runtime = new ServerRuntime(ServerRuntime.detect(), minecraftVersion());
        scheduler = new ServerScheduler(this);
        if (runtime.kind() == ServerRuntime.Kind.FOLIA && !scheduler.paperScheduling()) {
            getLogger().severe("Folia was detected, but the region scheduler is missing. BFFA01 will not enable.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("BFFA01 " + getDescription().getVersion() + " on " + runtime.describe()
                + " (Java " + Runtime.version().feature() + ", api-version 1.20).");

        messages = Messages.load(getDataFolder().toPath(), getConfig().getString("language", "en"), getConfig());
        services = new ServiceRegistry();
        services.register(PlaceholderResolver.class, PlaceholderResolver.identity());
        services.register(MetricsPublisher.class, MetricsPublisher.noop());
        services.register(WebhookPublisher.class, WebhookPublisher.noop());
        integrations = new IntegrationBus();
        Integrations.registerDefaults(integrations, services);

        dataManager = new DataManager(this);
        dataManager.useRunner(scheduler);
        killstreakManager = new KillstreakManager(this);
        kitManager = new KitManager(this);
        gameListener = new GameListener(this);
        mapManager = new MapManager(this);
        scoreboardManager = new ScoreboardManager(this);
        hologramManager = new HologramManager(this);

        getServer().getPluginManager().registerEvents(gameListener, this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new IntegrationListener(this), this);

        bind("bffa", new BuildFFAAdminCommand(this));
        bind("stats", new StatsCommand(this));
        bind("kit", new KitEditorCommand(this));
        bind("savekit", new SaveKitCommand(this));
        bind("shop", new ShopCommand(this));

        mapManager.startMapRotation();
        integrations.refresh(name -> getServer().getPluginManager().isPluginEnabled(name), getLogger(), this);
    }

    @Override
    public void onDisable() {
        try {
            if (scheduler != null) {
                scheduler.cancelAll();
            }
            if (gameListener != null) {
                gameListener.clearAllBlocks();
            }
            if (hologramManager != null) {
                hologramManager.clear();
            }
        } finally {
            if (integrations != null) {
                integrations.refresh(name -> false, getLogger(), this);
            }
            if (dataManager != null) {
                dataManager.flush();
            }
        }
    }

    public void reloadPluginConfig() {
        reloadConfig();
        messages = Messages.load(getDataFolder().toPath(), getConfig().getString("language", "en"), getConfig());
        if (scoreboardManager != null) {
            scoreboardManager.reloadConfig();
        }
        if (mapManager != null) {
            mapManager.loadMaps();
        }
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    private void saveLanguage(String language) {
        String path = "lang/" + language + ".yml";
        if (!new File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    private String minecraftVersion() {
        try {
            return Bukkit.class.getMethod("getMinecraftVersion").invoke(null).toString();
        } catch (Throwable ignored) {
            return Bukkit.getBukkitVersion();
        }
    }

    public KitManager getKitManager() { return kitManager; }
    public MapManager getMapManager() { return mapManager; }
    public DataManager getDataManager() { return dataManager; }
    public KillstreakManager getKillstreakManager() { return killstreakManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public GameListener getGameListener() { return gameListener; }
    public Messages getMessages() { return messages; }
    public ServiceRegistry services() { return services; }
    public ServerScheduler scheduler() { return scheduler; }
    public IntegrationBus integrations() { return integrations; }
    public ServerRuntime runtime() { return runtime; }
}
