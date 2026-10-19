package infinode.org.example.bFFA01.compat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Routes work onto the main thread, an entity's region, or a chunk region.
 * Paper-only types are loaded reflectively so the jar still links on Spigot.
 */
public final class ServerScheduler implements TaskRunner {

    private final Plugin plugin;
    private final boolean paper;
    private final boolean folia;
    private volatile boolean warned;

    public ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.folia = ServerRuntime.detect() == ServerRuntime.Kind.FOLIA;
        this.paper = this.folia || hasMethod(Bukkit.class, "getGlobalRegionScheduler");
    }

    public boolean paperScheduling() {
        return paper;
    }

    public boolean folia() {
        return folia;
    }

    @Override
    public Cancellable runLater(Runnable task, long delayTicks) {
        if (!paper) {
            return bukkitLater(task, delayTicks);
        }
        try {
            Object global = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            if (delayTicks <= 0L) {
                Method run = method(global, "run", 2);
                Object handle = run.invoke(global, plugin, consumer(task));
                return cancel(handle);
            }
            Method runDelayed = method(global, "runDelayed", 3);
            Object handle = runDelayed.invoke(global, plugin, consumer(task), delayTicks);
            return cancel(handle);
        } catch (Throwable ex) {
            warn(ex);
            return bukkitLater(task, delayTicks);
        }
    }

    @Override
    public void runAsync(Runnable task) {
        if (!paper) {
            bukkitAsync(task);
            return;
        }
        try {
            Object async = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Method runNow = method(async, "runNow", 2);
            runNow.invoke(async, plugin, consumer(task));
        } catch (Throwable ex) {
            warn(ex);
            bukkitAsync(task);
        }
    }

    @Override
    public void runSync(Runnable task) {
        if (!paper) {
            if (primary()) {
                task.run();
            } else {
                bukkitSync(task);
            }
            return;
        }
        try {
            Object global = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method execute = method(global, "execute", 2);
            execute.invoke(global, plugin, task);
        } catch (Throwable ex) {
            warn(ex);
            if (primary()) {
                task.run();
            } else {
                bukkitSync(task);
            }
        }
    }

    public Cancellable runTimer(Runnable task, long delayTicks, long periodTicks) {
        long period = Math.max(1L, periodTicks);
        if (!paper) {
            try {
                return cancel(plugin.getServer().getScheduler().runTaskTimer(plugin, task, Math.max(0L, delayTicks), period));
            } catch (Throwable ex) {
                warn(ex);
                return Cancellable.NONE;
            }
        }
        try {
            Object global = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Method rate = method(global, "runAtFixedRate", 4);
            Object handle = rate.invoke(global, plugin, consumer(task), Math.max(1L, delayTicks), period);
            return cancel(handle);
        } catch (Throwable ex) {
            warn(ex);
            return Cancellable.NONE;
        }
    }

    public void runOnEntity(Entity entity, Runnable task) {
        if (entity == null) {
            runSync(task);
            return;
        }
        if (!paper) {
            if (primary()) {
                task.run();
            } else {
                bukkitSync(task);
            }
            return;
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method execute = method(scheduler, "execute", 4);
            execute.invoke(scheduler, plugin, task, null, 0L);
        } catch (Throwable ex) {
            warn(ex);
            runSync(task);
        }
    }

    public Cancellable runOnEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null || !paper) {
            return runLater(task, delayTicks);
        }
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method delayed = method(scheduler, "runDelayed", 4);
            Object handle = delayed.invoke(scheduler, plugin, consumer(task), null, Math.max(1L, delayTicks));
            return cancel(handle);
        } catch (Throwable ex) {
            warn(ex);
            return runLater(task, delayTicks);
        }
    }

    public void runAt(Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null || !paper) {
            if (delayTicks <= 0L && primary()) {
                task.run();
            } else {
                runLater(task, delayTicks);
            }
            return;
        }
        try {
            Object region = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            if (delayTicks <= 0L) {
                Method execute = findLocation(region, "execute", 3);
                execute.invoke(region, plugin, location, task);
                return;
            }
            Method delayed = findLocation(region, "runDelayed", 4);
            delayed.invoke(region, plugin, location, consumer(task), delayTicks);
        } catch (Throwable ex) {
            warn(ex);
            runLater(task, delayTicks);
        }
    }

    public void teleport(org.bukkit.entity.Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        try {
            Method async = null;
            for (Method method : player.getClass().getMethods()) {
                if (!"teleportAsync".equals(method.getName()) || method.getParameterCount() < 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(Location.class) && method.getParameterCount() == 1) {
                    async = method;
                    break;
                }
            }
            if (async != null) {
                async.invoke(player, location);
                return;
            }
        } catch (Throwable ex) {
            warn(ex);
        }
        player.teleport(location);
    }

    public void cancelAll() {
        try {
            plugin.getServer().getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
            // Folia rejects the global Bukkit scheduler.
        }
        cancelNamed("getGlobalRegionScheduler");
        cancelNamed("getAsyncScheduler");
    }

    private void cancelNamed(String bukkitMethod) {
        if (!paper) {
            return;
        }
        try {
            Object scheduler = Bukkit.class.getMethod(bukkitMethod).invoke(null);
            scheduler.getClass().getMethod("cancelTasks", Plugin.class).invoke(scheduler, plugin);
        } catch (Throwable ignored) {
            // The server has no matching scheduler.
        }
    }

    private Cancellable bukkitLater(Runnable task, long delayTicks) {
        try {
            return cancel(plugin.getServer().getScheduler().runTaskLater(plugin, task, Math.max(0L, delayTicks)));
        } catch (Throwable ex) {
            warn(ex);
            task.run();
            return Cancellable.NONE;
        }
    }

    private void bukkitAsync(Runnable task) {
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Throwable ex) {
            Thread thread = new Thread(task, "bffa-io");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void bukkitSync(Runnable task) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (Throwable ex) {
            warn(ex);
        }
    }

    private boolean primary() {
        try {
            return plugin.getServer().isPrimaryThread();
        } catch (Throwable ex) {
            return true;
        }
    }

    private void warn(Throwable ex) {
        if (warned) {
            return;
        }
        warned = true;
        plugin.getLogger().warning("Scheduler bridge fell back after: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Cancellable cancel(Object handle) {
        if (handle == null) {
            return Cancellable.NONE;
        }
        return () -> {
            try {
                handle.getClass().getMethod("cancel").invoke(handle);
            } catch (Throwable ignored) {
                // Already finished.
            }
        };
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Method method(Object target, String name, int parameters) throws NoSuchMethodException {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == parameters) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(name + "/" + parameters);
    }

    private static Method findLocation(Object target, String name, int parameters) throws NoSuchMethodException {
        for (Method candidate : target.getClass().getMethods()) {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != parameters) {
                continue;
            }
            if (candidate.getParameterCount() >= 2 && Location.class.isAssignableFrom(candidate.getParameterTypes()[1])) {
                return candidate;
            }
        }
        throw new NoSuchMethodException(name + "/location");
    }

}
