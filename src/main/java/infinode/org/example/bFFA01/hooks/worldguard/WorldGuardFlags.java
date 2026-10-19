package infinode.org.example.bFFA01.hooks.worldguard;

import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Registers {@code bffa-build} and {@code bffa-pvp} during {@code onLoad}, which is
 * the only moment WorldGuard still accepts new flags. Both default to allow.
 */
public final class WorldGuardFlags {

    private static volatile Object buildFlag;
    private static volatile Object pvpFlag;

    private WorldGuardFlags() {
    }

    public static void register(Plugin owner, Logger logger) {
        ClassLoader loader = worldGuardLoader(owner);
        if (loader == null) {
            return;
        }
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object registry = instance.getClass().getMethod("getFlagRegistry").invoke(instance);
            Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
            Class<?> flagType = Class.forName("com.sk89q.worldguard.protection.flags.Flag", true, loader);
            buildFlag = define(registry, stateFlag, flagType, "bffa-build", logger);
            pvpFlag = define(registry, stateFlag, flagType, "bffa-pvp", logger);
        } catch (ClassNotFoundException ignored) {
            buildFlag = null;
            pvpFlag = null;
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("WorldGuard flags were not registered: " + root(ex));
            }
        }
    }

    public static Object buildFlag() {
        return buildFlag;
    }

    public static Object pvpFlag() {
        return pvpFlag;
    }

    public static boolean registered() {
        return buildFlag != null || pvpFlag != null;
    }

    /**
     * WorldGuard lives in its own plugin class loader. The plugin loader cannot see those
     * classes, so flag registration has to borrow WorldGuard's loader during {@code onLoad}.
     */
    private static ClassLoader worldGuardLoader(Plugin owner) {
        if (owner == null) {
            return null;
        }
        try {
            Plugin worldGuard = owner.getServer().getPluginManager().getPlugin("WorldGuard");
            return worldGuard == null ? null : worldGuard.getClass().getClassLoader();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object define(Object registry, Class<?> stateFlag, Class<?> flagType, String name, Logger logger) {
        Object existing = Reflects.call(registry, "get", name);
        if (existing != null) {
            return existing;
        }
        try {
            Object flag = stateFlag.getConstructor(String.class, boolean.class).newInstance(name, true);
            registry.getClass().getMethod("register", flagType).invoke(registry, flag);
            return flag;
        } catch (Throwable ex) {
            Object again = Reflects.call(registry, "get", name);
            if (again != null) {
                return again;
            }
            if (logger != null) {
                logger.warning("WorldGuard flag " + name + " was not registered: " + root(ex));
            }
            return null;
        }
    }

    private static String root(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
