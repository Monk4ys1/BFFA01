package infinode.org.example.bFFA01.hooks.worldguard;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;

/**
 * Region membership and the {@code bffa-build} / {@code bffa-pvp} state flags.
 */
public final class WorldGuardRegions {

    private volatile boolean active;
    private volatile Plugin worldGuard;

    public void bind(Plugin plugin) {
        this.worldGuard = plugin;
        this.active = plugin != null;
    }

    public void unbind() {
        this.worldGuard = null;
        this.active = false;
    }

    public boolean active() {
        return active;
    }

    public boolean allowsBuild(BFFA01 plugin, Location location) {
        return allows(plugin, location, true);
    }

    public boolean allowsPvp(BFFA01 plugin, Location location) {
        return allows(plugin, location, false);
    }

    private boolean allows(BFFA01 plugin, Location location, boolean build) {
        if (!active || plugin == null) {
            return true;
        }
        var config = IntegrationSettings.config(plugin);
        if (!IntegrationSettings.enabled(config, "worldguard", true)) {
            return true;
        }
        String map = "";
        try {
            if (plugin.getMapManager() != null && plugin.getMapManager().getCurrentMap() != null) {
                map = plugin.getMapManager().getCurrentMap();
            }
        } catch (Throwable ignored) {
            map = "";
        }
        String region = IntegrationSettings.arenaRegion(config, map);
        boolean restriction = build
                ? IntegrationSettings.denyBuildOutside(config)
                : IntegrationSettings.denyPvpOutside(config);
        Boolean flag = IntegrationSettings.worldGuardFlags(config) ? flagAllows(location, build) : null;
        boolean inside = region.isEmpty() || inside(location, region);
        return ArenaDecision.allows(true, restriction, !region.isEmpty(), inside, flag);
    }

    private Boolean flagAllows(Location location, boolean build) {
        Object flag = build ? WorldGuardFlags.buildFlag() : WorldGuardFlags.pvpFlag();
        if (flag == null || location == null) {
            return null;
        }
        Object set = regions(location);
        if (set == null) {
            return null;
        }
        Object flags = Array.newInstance(flag.getClass(), 1);
        Array.set(flags, 0, flag);
        Object result = Reflects.call(set, "testState", null, flags);
        return result instanceof Boolean allowed ? allowed : null;
    }

    private boolean inside(Location location, String regionId) {
        Object set = regions(location);
        if (set == null) {
            return true;
        }
        Object regions = Reflects.call(set, "getRegions");
        if (!(regions instanceof Iterable<?> iterable)) {
            return true;
        }
        for (Object region : iterable) {
            Object id = Reflects.call(region, "getId");
            if (id instanceof String text && text.equalsIgnoreCase(regionId)) {
                return true;
            }
        }
        return false;
    }

    private Object regions(Location location) {
        Plugin plugin = worldGuard;
        if (plugin == null || location == null || location.getWorld() == null) {
            return null;
        }
        try {
            ClassLoader loader = plugin.getClass().getClassLoader();
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = Reflects.call(instance, "getPlatform");
            Object container = Reflects.call(platform, "getRegionContainer");
            Object query = Reflects.call(container, "createQuery");
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
            Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
            return Reflects.call(query, "getApplicableRegions", adapted);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
