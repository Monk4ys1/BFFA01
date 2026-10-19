package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockForms;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockPlayers;
import infinode.org.example.bFFA01.hooks.hologram.DecentHologramsBackend;
import infinode.org.example.bFFA01.hooks.hologram.FancyHologramsBackend;
import infinode.org.example.bFFA01.hooks.hologram.HologramHub;
import infinode.org.example.bFFA01.hooks.luck.LuckPermsHook;
import infinode.org.example.bFFA01.hooks.luck.Prefixes;
import infinode.org.example.bFFA01.hooks.mini.MiniPlaceholderBridge;
import infinode.org.example.bFFA01.hooks.papi.BffaPlaceholderResolver;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.hooks.vault.VaultBridge;
import infinode.org.example.bFFA01.hooks.vault.VaultEconomy;
import infinode.org.example.bFFA01.hooks.via.ViaPlayers;
import infinode.org.example.bFFA01.hooks.worldguard.WorldGuardRegions;
import infinode.org.example.bFFA01.service.ServiceRegistry;
import org.bukkit.plugin.Plugin;

/**
 * Soft-dependency hooks. Each one stays quiet when its plugin is missing or disabled in config.
 */
public final class Integrations {

    private Integrations() {
    }

    public static void registerDefaults(IntegrationBus bus, ServiceRegistry services) {
        bus.register(new PlaceholderApiIntegration());
        bus.register(new VaultIntegration());
        bus.register(new LuckPermsIntegration());
        bus.register(new WorldGuardIntegration());
        bus.register(new DecentHologramsIntegration());
        bus.register(new FancyHologramsIntegration());
        bus.register(new GeyserIntegration());
        bus.register(new FloodgateIntegration());
        bus.register(new ViaVersionIntegration());
        bus.register(new MiniPlaceholdersIntegration());
    }

    private static boolean enabled(BFFA01 plugin, String id) {
        return IntegrationSettings.enabled(IntegrationSettings.config(plugin), id, true);
    }

    private static Plugin plugin(BFFA01 plugin, String name) {
        if (plugin == null || plugin.getServer() == null) {
            return null;
        }
        return plugin.getServer().getPluginManager().getPlugin(name);
    }

    public static final class PlaceholderApiIntegration implements Integration {
        private BFFA01 owner;
        private Object expansion;

        @Override
        public String id() {
            return "placeholderapi";
        }

        @Override
        public String pluginName() {
            return "PlaceholderAPI";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            this.owner = plugin;
            downstream(plugin, new ReflectivePlaceholderResolver());
            try {
                Class<?> type = Class.forName("infinode.org.example.bFFA01.hooks.papi.BffaExpansion");
                expansion = type.getConstructor(BFFA01.class).newInstance(plugin);
                Object registered = type.getMethod("register").invoke(expansion);
                if (!(registered instanceof Boolean ok) || !ok) {
                    expansion = null;
                    throw new IllegalStateException("PlaceholderAPI did not accept the bffa expansion");
                }
            } catch (RuntimeException ex) {
                throw ex;
            } catch (Throwable ex) {
                expansion = null;
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }

        @Override
        public void disable() {
            Object current = expansion;
            expansion = null;
            if (current != null) {
                try {
                    current.getClass().getMethod("unregister").invoke(current);
                } catch (Throwable ignored) {
                    // PlaceholderAPI may already be gone.
                }
            }
            if (owner != null) {
                downstream(owner, PlaceholderResolver.identity());
            }
        }

        private static void downstream(BFFA01 plugin, PlaceholderResolver resolver) {
            try {
                if (plugin.services() == null) {
                    return;
                }
                plugin.services().get(BffaPlaceholderResolver.class).ifPresent(chain -> chain.downstream(resolver));
            } catch (Throwable ignored) {
                // The scoreboard still has its own tokens.
            }
        }
    }

    public static final class VaultIntegration implements Integration {
        private BFFA01 owner;

        @Override
        public String id() {
            return "vault";
        }

        @Override
        public String pluginName() {
            return "Vault";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            this.owner = plugin;
            VaultBridge bridge = VaultEconomy.attach(plugin(plugin, pluginName()), plugin.getLogger());
            router(plugin).vault(bridge);
            plugin.services().get(Prefixes.class).orElseThrow().vault(player -> bridge.chatPrefix(player.getUniqueId()));
            plugin.services().register(VaultBridge.class, bridge);
        }

        @Override
        public void disable() {
            if (owner == null || owner.services() == null) {
                return;
            }
            router(owner).vault(VaultBridge.UNAVAILABLE);
            owner.services().get(Prefixes.class).ifPresent(prefixes -> prefixes.vault(null));
            owner.services().register(VaultBridge.class, VaultBridge.UNAVAILABLE);
        }

        @Override
        public void reload(BFFA01 plugin) {
            enable(plugin);
        }

        private static CurrencyRouter router(BFFA01 plugin) {
            return plugin.services().get(CurrencyRouter.class).orElseThrow();
        }
    }

    public static final class LuckPermsIntegration implements Integration {
        private LuckPermsHook hook;

        @Override
        public String id() {
            return "luckperms";
        }

        @Override
        public String pluginName() {
            return "LuckPerms";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            Prefixes prefixes = plugin.services().get(Prefixes.class).orElseThrow();
            hook = new LuckPermsHook(prefixes);
            hook.enable(plugin, plugin(plugin, pluginName()), plugin.getLogger());
            plugin.services().register(LuckPermsHook.class, hook);
        }

        @Override
        public void disable() {
            if (hook != null) {
                hook.disable();
                hook = null;
            }
        }
    }

    public static final class WorldGuardIntegration implements Integration {
        private WorldGuardRegions regions;
        @Override
        public String id() {
            return "worldguard";
        }

        @Override
        public String pluginName() {
            return "WorldGuard";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            regions = plugin.services().get(WorldGuardRegions.class).orElseThrow();
            regions.bind(plugin(plugin, pluginName()));
        }

        @Override
        public void disable() {
            if (regions != null) {
                regions.unbind();
            }
        }
    }

    public static final class DecentHologramsIntegration implements Integration {
        private DecentHologramsBackend backend;
        private HologramHub hub;
        @Override
        public String id() {
            return "decentholograms";
        }

        @Override
        public String pluginName() {
            return "DecentHolograms";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            backend = new DecentHologramsBackend();
            backend.bind(plugin(plugin, pluginName()));
            hub = plugin.services().get(HologramHub.class).orElseThrow();
            hub.decent(backend);
        }

        @Override
        public void disable() {
            if (backend != null) {
                backend.unbind();
                backend = null;
            }
            if (hub != null) {
                hub.decent(null);
            }
        }
    }

    public static final class FancyHologramsIntegration implements Integration {
        private FancyHologramsBackend backend;
        private HologramHub hub;
        @Override
        public String id() {
            return "fancyholograms";
        }

        @Override
        public String pluginName() {
            return "FancyHolograms";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            backend = new FancyHologramsBackend();
            backend.bind(plugin(plugin, pluginName()));
            hub = plugin.services().get(HologramHub.class).orElseThrow();
            hub.fancy(backend);
        }

        @Override
        public void disable() {
            if (backend != null) {
                backend.unbind();
                backend = null;
            }
            if (hub != null) {
                hub.fancy(null);
            }
        }
    }

    public static final class GeyserIntegration implements Integration {
        @Override
        public String id() {
            return "geyser";
        }

        @Override
        public String pluginName() {
            return "Geyser-Spigot";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            Plugin geyser = plugin(plugin, pluginName());
            if (geyser == null) {
                throw new IllegalStateException("Geyser is not loaded");
            }
            Class<?> apiType = Reflects.load(geyser.getClass().getClassLoader(), "org.geysermc.geyser.api.GeyserApi");
            if (apiType == null) {
                throw new IllegalStateException("Geyser API is not visible");
            }
            BedrockPlayers players = plugin.services().get(BedrockPlayers.class).orElseThrow();
            players.use(id -> {
                Object api = Reflects.callStatic(apiType, "api");
                if (api == null) {
                    api = Reflects.callStatic(apiType, "getInstance");
                }
                return Reflects.call(api, "connectionByUuid", id) != null;
            });
        }

        @Override
        public void disable() {
        }
    }

    public static final class FloodgateIntegration implements Integration {
        @Override
        public String id() {
            return "floodgate";
        }

        @Override
        public String pluginName() {
            return "floodgate";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            Plugin floodgate = plugin(plugin, pluginName());
            if (floodgate == null) {
                throw new IllegalStateException("Floodgate is not loaded");
            }
            Class<?> apiType = Reflects.load(floodgate.getClass().getClassLoader(), "org.geysermc.floodgate.api.FloodgateApi");
            if (apiType == null) {
                throw new IllegalStateException("Floodgate API is not visible");
            }
            BedrockPlayers players = plugin.services().get(BedrockPlayers.class).orElseThrow();
            players.use(id -> {
                Object api = Reflects.callStatic(apiType, "getInstance");
                Object result = Reflects.call(api, "isFloodgatePlayer", id);
                return result instanceof Boolean bedrock && bedrock;
            });
        }

        @Override
        public void disable() {
        }
    }

    public static final class ViaVersionIntegration implements Integration {
        private ViaPlayers via;

        @Override
        public String id() {
            return "viaversion";
        }

        @Override
        public String pluginName() {
            return "ViaVersion";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            via = plugin.services().get(ViaPlayers.class).orElseThrow();
            via.bind(plugin(plugin, pluginName()));
        }

        @Override
        public void disable() {
            if (via != null) {
                via.unbind();
            }
        }
    }

    public static final class MiniPlaceholdersIntegration implements Integration {
        private final MiniPlaceholderBridge bridge = new MiniPlaceholderBridge();

        @Override
        public String id() {
            return "miniplaceholders";
        }

        @Override
        public String pluginName() {
            return "MiniPlaceholders";
        }

        @Override
        public boolean available(BFFA01 plugin) {
            return enabled(plugin, id());
        }

        @Override
        public void enable(BFFA01 plugin) {
            bridge.enable(plugin, plugin(plugin, pluginName()));
        }

        @Override
        public void disable() {
            bridge.disable();
        }
    }

    /**
     * Keeps the Bedrock form types referenced from gameplay code in one place.
     */
    public static boolean openBedrockShop(BFFA01 plugin, org.bukkit.entity.Player player) {
        return BedrockForms.openShop(plugin, player);
    }

    public static boolean openBedrockStats(BFFA01 plugin, org.bukkit.entity.Player player) {
        return BedrockForms.openStats(plugin, player);
    }
}
