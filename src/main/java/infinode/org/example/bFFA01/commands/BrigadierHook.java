package infinode.org.example.bFFA01.commands;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Adds Brigadier literal children when the server exposes them.
 * Tab completion stays the path that works on every server.
 */
public final class BrigadierHook {

    private static final String[] EVENT_TYPES = {
            "com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent",
            "io.papermc.paper.event.brigadier.CommandRegisteredEvent"
    };

    private BrigadierHook() {
    }

    public static boolean install(Plugin plugin, Map<String, List<String>> literalsByLabel) {
        Class<? extends Event> eventType = eventType();
        if (eventType == null || plugin == null || literalsByLabel == null) {
            return false;
        }
        try {
            plugin.getServer().getPluginManager().registerEvent(
                    eventType,
                    new Listener() {
                    },
                    EventPriority.NORMAL,
                    (listener, event) -> attach(event, literalsByLabel),
                    plugin
            );
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().info("Brigadier suggestions were not attached: " + ex.getMessage());
            return false;
        }
    }

    static boolean eventPresent() {
        return eventType() != null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventType() {
        for (String name : EVENT_TYPES) {
            try {
                Class<?> type = Class.forName(name);
                if (Event.class.isAssignableFrom(type)) {
                    return (Class<? extends Event>) type;
                }
            } catch (Throwable ignored) {
                // This server build does not ship that event.
            }
        }
        return null;
    }

    private static void attach(Event event, Map<String, List<String>> literalsByLabel) {
        try {
            Object label = event.getClass().getMethod("getCommandLabel").invoke(event);
            if (!(label instanceof String command) || !literalsByLabel.containsKey(command)) {
                return;
            }
            List<String> subs = literalsByLabel.get(command);
            if (subs == null || subs.isEmpty()) {
                return;
            }
            Object builder = event.getClass().getMethod("getLiteral").invoke(event);
            Class<?> literals = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder");
            Method literal = literals.getMethod("literal", String.class);
            Method then = null;
            for (Method method : builder.getClass().getMethods()) {
                if ("then".equals(method.getName()) && method.getParameterCount() == 1) {
                    then = method;
                    break;
                }
            }
            if (then == null) {
                return;
            }
            for (String sub : subs) {
                Object child = literal.invoke(null, sub);
                then.invoke(builder, child);
            }
        } catch (Throwable ignored) {
            // Leave the Bukkit command node untouched.
        }
    }
}
