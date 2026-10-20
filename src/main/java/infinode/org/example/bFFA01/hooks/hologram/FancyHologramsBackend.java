package infinode.org.example.bFFA01.hooks.hologram;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;

/**
 * FancyHolograms through its API plugin class. Text is passed as strings. If that
 * build only accepts components, the call fails and the armor-stand fallback is used.
 */
public final class FancyHologramsBackend implements HologramBackend {

    private volatile boolean bound;
    private volatile ClassLoader loader;
    private String lastId = "bffa-top";

    public void bind(Plugin plugin) {
        Class<?> type = Reflects.load(plugin.getClass().getClassLoader(), "de.oliver.fancyholograms.api.FancyHologramsPlugin");
        if (type == null) {
            throw new IllegalStateException("FancyHolograms API is not visible");
        }
        Object api = Reflects.callStatic(type, "get");
        if (api == null) {
            api = Reflects.callStatic(type, "getInstance");
        }
        if (api == null || Reflects.call(api, "getHologramManager") == null) {
            throw new IllegalStateException("FancyHolograms manager is missing");
        }
        this.loader = plugin.getClass().getClassLoader();
        this.bound = true;
    }

    public void unbind() {
        clear();
        bound = false;
        loader = null;
    }

    @Override
    public boolean available() {
        return bound && loader != null;
    }

    @Override
    public boolean show(BFFA01 plugin, Location location, List<String> lines) {
        if (!available() || location == null || lines == null) {
            return false;
        }
        String id = IntegrationSettings.hologramId(IntegrationSettings.config(plugin));
        lastId = id;
        try {
            clearId(id);
            Object manager = manager();
            Class<?> dataType = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData", true, loader);
            Object data = dataType.getConstructor(String.class, Location.class).newInstance(id, location);
            Reflects.invoke(data, "setPersistent", false);
            if (!Reflects.invoke(data, "setText", lines)) {
                plugin.getLogger().warning("FancyHolograms rejected the leaderboard text. Using armor stands.");
                return false;
            }
            return spawn(manager, data);
        } catch (Throwable ex) {
            plugin.getLogger().warning("FancyHolograms did not spawn the leaderboard: " + ex.getMessage());
            clearId(id);
            return false;
        }
    }

    @Override
    public void clear() {
        if (bound) {
            clearId(lastId);
        }
    }

    private void clearId(String id) {
        try {
            Object manager = manager();
            Object existing = Reflects.call(manager, "getHologram", id);
            if (existing instanceof Optional<?> optional) {
                existing = optional.orElse(null);
            }
            if (existing != null) {
                if (!Reflects.invoke(manager, "removeHologram", existing)) {
                    Reflects.invoke(existing, "deleteHologram");
                }
            }
        } catch (Throwable ignored) {
            // Already removed.
        }
    }

    private boolean spawn(Object manager, Object data) {
        for (java.lang.reflect.Method method : manager.getClass().getMethods()) {
            if (!"create".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isInstance(data)) {
                continue;
            }
            try {
                Object created = method.invoke(manager, data);
                if (method.getReturnType() == void.class) {
                    return true;
                }
                if (created == null) {
                    return false;
                }
                if (!Reflects.invoke(manager, "addHologram", created)) {
                    Reflects.invoke(manager, "register", created);
                }
                return true;
            } catch (Throwable ignored) {
                // Try another create method.
            }
        }
        return false;
    }

    private Object manager() throws ReflectiveOperationException {
        Class<?> type = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin", true, loader);
        Object api = Reflects.callStatic(type, "get");
        if (api == null) {
            api = type.getMethod("get").invoke(null);
        }
        Object manager = Reflects.call(api, "getHologramManager");
        if (manager == null) {
            throw new IllegalStateException("manager");
        }
        return manager;
    }
}
