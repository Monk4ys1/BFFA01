package infinode.org.example.bFFA01.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Static lookup so another plugin can call {@code BuildFFAProvider.get()} without
 * timing its onEnable against the services manager.
 */
public final class BuildFFAProvider {

    private static volatile BuildFFA instance;

    private BuildFFAProvider() {
    }

    public static BuildFFA get() {
        BuildFFA current = instance;
        if (current != null) {
            return current;
        }
        try {
            RegisteredServiceProvider<BuildFFA> registration = Bukkit.getServicesManager().getRegistration(BuildFFA.class);
            return registration == null ? null : registration.getProvider();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void bind(BuildFFA api) {
        instance = api;
    }
}
