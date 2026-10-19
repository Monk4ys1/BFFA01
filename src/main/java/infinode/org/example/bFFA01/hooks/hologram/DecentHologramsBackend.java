package infinode.org.example.bFFA01.hooks.hologram;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * DecentHolograms via {@code DHAPI}. The hologram is not written into DecentHolograms' own files.
 */
public final class DecentHologramsBackend implements HologramBackend {

    private volatile boolean bound;
    private volatile Class<?> api;
    private String lastId = "bffa-top";

    public void bind(Plugin plugin) {
        Class<?> type = Reflects.load(plugin.getClass().getClassLoader(), "eu.decentsoftware.holograms.api.DHAPI");
        if (type == null) {
            throw new IllegalStateException("DHAPI is not on the DecentHolograms classpath");
        }
        this.api = type;
        this.bound = true;
    }

    public void unbind() {
        clear();
        bound = false;
        api = null;
    }

    @Override
    public boolean available() {
        return bound && api != null;
    }

    @Override
    public boolean show(BFFA01 plugin, Location location, List<String> lines) {
        if (!available() || location == null || lines == null) {
            return false;
        }
        String id = IntegrationSettings.hologramId(IntegrationSettings.config(plugin));
        lastId = id;
        try {
            remove(id);
            if (!create(id, location, lines)) {
                return false;
            }
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().warning("DecentHolograms did not spawn the leaderboard: " + ex.getMessage());
            remove(id);
            return false;
        }
    }

    @Override
    public void clear() {
        if (api != null) {
            remove(lastId);
        }
    }

    private void remove(String id) {
        Reflects.callStatic(api, "removeHologram", id);
    }

    private boolean create(String id, Location location, List<String> lines) {
        for (Method method : api.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || !"createHologram".equals(method.getName())) {
                continue;
            }
            Object[] args = arguments(method.getParameterTypes(), id, location, lines);
            if (args == null) {
                continue;
            }
            try {
                method.invoke(null, args);
                return true;
            } catch (Throwable ignored) {
                // Try the next overload.
            }
        }
        return false;
    }

    private static Object[] arguments(Class<?>[] types, String id, Location location, List<String> lines) {
        if (types.length == 3 && types[0] == String.class && types[1] == Location.class && List.class.isAssignableFrom(types[2])) {
            return new Object[]{id, location, lines};
        }
        if (types.length == 4 && types[0] == String.class && types[1] == Location.class && types[2] == boolean.class
                && List.class.isAssignableFrom(types[3])) {
            return new Object[]{id, location, false, lines};
        }
        if (types.length == 4 && types[0] == String.class && types[1] == Location.class
                && List.class.isAssignableFrom(types[2]) && types[3] == boolean.class) {
            return new Object[]{id, location, lines, false};
        }
        return null;
    }
}
