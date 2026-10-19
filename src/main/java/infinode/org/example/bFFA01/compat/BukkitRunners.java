package infinode.org.example.bFFA01.compat;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit scheduler used when the plugin has not installed {@link ServerScheduler} yet.
 */
public final class BukkitRunners {

    private BukkitRunners() {
    }

    public static TaskRunner global(JavaPlugin plugin) {
        return new ServerScheduler(plugin);
    }
}
