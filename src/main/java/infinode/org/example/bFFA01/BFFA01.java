package infinode.org.example.bFFA01;

import infinode.org.example.bFFA01.commands.BrigadierHook;
import infinode.org.example.bFFA01.commands.BuildFFAAdminCommand;
import infinode.org.example.bFFA01.commands.KitEditorCommand;
import infinode.org.example.bFFA01.commands.MenuCommand;
import infinode.org.example.bFFA01.commands.SaveKitCommand;
import infinode.org.example.bFFA01.commands.ShopCommand;
import infinode.org.example.bFFA01.commands.StatsCommand;
import infinode.org.example.bFFA01.compat.ServerRuntime;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.config.ConfigMigrator;
import infinode.org.example.bFFA01.api.BuildFFA;
import infinode.org.example.bFFA01.api.BuildFFAProvider;
import infinode.org.example.bFFA01.hooks.IntegrationBus;
import infinode.org.example.bFFA01.hooks.Integrations;
import infinode.org.example.bFFA01.hooks.LiveBuildFFA;
import infinode.org.example.bFFA01.hooks.MatchEvents;
import infinode.org.example.bFFA01.hooks.WebhookPublisher;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockPlayers;
import infinode.org.example.bFFA01.hooks.discord.DiscordWebhook;
import infinode.org.example.bFFA01.hooks.hologram.HologramHub;
import infinode.org.example.bFFA01.hooks.luck.Prefixes;
import infinode.org.example.bFFA01.hooks.metrics.BstatsMetrics;
import infinode.org.example.bFFA01.hooks.papi.BffaPlaceholderResolver;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.hooks.vault.VaultBridge;
import infinode.org.example.bFFA01.hooks.via.ViaPlayers;
import infinode.org.example.bFFA01.hooks.worldguard.WorldGuardFlags;
import infinode.org.example.bFFA01.hooks.worldguard.WorldGuardRegions;
import infinode.org.example.bFFA01.listeners.GameListener;
import infinode.org.example.bFFA01.listeners.IntegrationListener;
import infinode.org.example.bFFA01.listeners.ShopListener;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.ui.chat.ChatListener;
import infinode.org.example.bFFA01.ui.menu.MenuListener;
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
import org.bukkit.plugin.ServicePriority;
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
    private Presentation presentation;
    private BstatsMetrics metrics;

    @Override
    public void onLoad() {
        BrigadierHook.install(this, Map.of(
                "bffa", List.of("setmap", "swapmap", "addcoins", "removecoins", "resetstats", "reload")
        ));
        WorldGuardFlags.register(this, getLogger());
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
        services.register(infinode.org.example.bFFA01.hooks.PlaceholderResolver.class, new BffaPlaceholderResolver(this));
        services.register(CurrencyRouter.class, new CurrencyRouter(this));
        services.register(VaultBridge.class, VaultBridge.UNAVAILABLE);
        services.register(Prefixes.class, new Prefixes());
        services.register(WorldGuardRegions.class, new WorldGuardRegions());
        services.register(HologramHub.class, new HologramHub());
        services.register(BedrockPlayers.class, new BedrockPlayers());
        services.register(ViaPlayers.class, new ViaPlayers());
        services.register(WebhookPublisher.class, new DiscordWebhook(this));
        metrics = new BstatsMetrics(this);
        services.register(infinode.org.example.bFFA01.hooks.MetricsPublisher.class, metrics);
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
        presentation = new Presentation(this);

        getServer().getPluginManager().registerEvents(gameListener, this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new IntegrationListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);

        bind("bffa", new BuildFFAAdminCommand(this));
        bind("stats", new StatsCommand(this));
        bind("kit", new KitEditorCommand(this));
        bind("savekit", new SaveKitCommand(this));
        bind("shop", new ShopCommand(this));
        MenuCommand menus = new MenuCommand(this);
        bind("menu", menus);
        bind("maps", menus);
        bind("settings", menus);

        mapManager.startMapRotation();
        integrations.refresh(name -> getServer().getPluginManager().isPluginEnabled(name), getLogger(), this);
        BuildFFA api = new LiveBuildFFA(this);
        BuildFFAProvider.bind(api);
        try {
            getServer().getServicesManager().register(BuildFFA.class, api, this, ServicePriority.Normal);
        } catch (Throwable ex) {
            getLogger().warning("Could not publish the BuildFFA service: " + ex.getMessage());
        }
        metrics.start();
        String active = integrations.activeIds().isEmpty() ? "none" : String.join(", ", integrations.activeIds());
        getLogger().info("Active integrations: " + active);
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
            if (metrics != null) {
                metrics.shutdown();
            }
            if (integrations != null) {
                integrations.refresh(name -> false, getLogger(), this);
            }
            if (presentation != null) {
                presentation.shutdown();
            }
            if (dataManager != null) {
                dataManager.flush();
            }
            BuildFFAProvider.bind(null);
            try {
                getServer().getServicesManager().unregisterAll(this);
            } catch (Throwable ignored) {
                // The server is already stopping.
            }
        }
    }

    public boolean reloadPluginConfig() {
        reloadConfig();
        messages = Messages.load(getDataFolder().toPath(), getConfig().getString("language", "en"), getConfig());
        if (presentation != null) {
            presentation.reload();
        }
        if (scoreboardManager != null) {
            scoreboardManager.reloadConfig();
        }
        if (mapManager != null) {
            mapManager.onConfigReload();
        }
        boolean storage = dataManager != null && dataManager.reconnect();
        if (integrations != null) {
            integrations.refresh(name -> getServer().getPluginManager().isPluginEnabled(name), getLogger(), this);
            integrations.reload(this);
        }
        if (mapManager != null) {
            mapManager.refreshLeaderboard();
        }
        MatchEvents.reload(this, storage);
        return storage;
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
    public Presentation presentation() { return presentation; }
}
