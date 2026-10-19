package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.service.ServiceRegistry;

/**
 * Soft-dependency hooks. Implementations stay empty until a later phase fills them in,
 * except PlaceholderAPI, which already expands tokens in scoreboard lines.
 */
public final class Integrations {

    private Integrations() {
    }

    public static void registerDefaults(IntegrationBus bus, ServiceRegistry services) {
        bus.register(new PlaceholderApiIntegration(services));
        bus.register(new VaultIntegration());
        bus.register(new LuckPermsIntegration());
        bus.register(new WorldGuardIntegration());
        bus.register(new DecentHologramsIntegration());
        bus.register(new FancyHologramsIntegration());
        bus.register(new GeyserIntegration());
        bus.register(new FloodgateIntegration());
        bus.register(new ViaVersionIntegration());
    }

    public static final class PlaceholderApiIntegration implements Integration {
        private final ServiceRegistry services;

        public PlaceholderApiIntegration(ServiceRegistry services) {
            this.services = services;
        }

        @Override
        public String id() {
            return "placeholderapi";
        }

        @Override
        public String pluginName() {
            return "PlaceholderAPI";
        }

        @Override
        public void enable(BFFA01 plugin) {
            services.register(PlaceholderResolver.class, new ReflectivePlaceholderResolver());
        }

        @Override
        public void disable() {
            services.register(PlaceholderResolver.class, PlaceholderResolver.identity());
        }
    }

    /** Economy hook. Coins stay internal until this registers an {@code EconomyBridge}. */
    public static final class VaultIntegration extends LoggingIntegration {
        public VaultIntegration() {
            super("vault", "Vault");
        }
    }

    /** Permission metadata hook. Command checks stay on Bukkit permissions until replaced. */
    public static final class LuckPermsIntegration extends LoggingIntegration {
        public LuckPermsIntegration() {
            super("luckperms", "LuckPerms");
        }
    }

    /** Region hook for spawn protection that should follow a WorldGuard region. */
    public static final class WorldGuardIntegration extends LoggingIntegration {
        public WorldGuardIntegration() {
            super("worldguard", "WorldGuard");
        }
    }

    /** Replacement for the internal armor-stand leaderboard. */
    public static final class DecentHologramsIntegration extends LoggingIntegration {
        public DecentHologramsIntegration() {
            super("decentholograms", "DecentHolograms");
        }
    }

    public static final class FancyHologramsIntegration extends LoggingIntegration {
        public FancyHologramsIntegration() {
            super("fancyholograms", "FancyHolograms");
        }
    }

    public static final class GeyserIntegration extends LoggingIntegration {
        public GeyserIntegration() {
            super("geyser", "Geyser-Spigot");
        }
    }

    public static final class FloodgateIntegration extends LoggingIntegration {
        public FloodgateIntegration() {
            super("floodgate", "floodgate");
        }
    }

    public static final class ViaVersionIntegration extends LoggingIntegration {
        public ViaVersionIntegration() {
            super("viaversion", "ViaVersion");
        }
    }

    private static class LoggingIntegration implements Integration {
        private final String id;
        private final String pluginName;

        LoggingIntegration(String id, String pluginName) {
            this.id = id;
            this.pluginName = pluginName;
        }

        @Override
        public final String id() {
            return id;
        }

        @Override
        public final String pluginName() {
            return pluginName;
        }

        @Override
        public final void enable(BFFA01 plugin) {
            onBind(plugin);
        }

        @Override
        public final void disable() {
            onUnbind();
        }

        protected void onBind(BFFA01 plugin) {
        }

        protected void onUnbind() {
        }
    }
}
