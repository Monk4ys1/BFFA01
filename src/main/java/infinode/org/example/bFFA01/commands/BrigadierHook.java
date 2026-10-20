package infinode.org.example.bFFA01.commands;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds Brigadier literal children when the server exposes them.
 * Registration waits until the plugin is enabled, then asks the server to sync commands
 * so the event fires after the listener exists. Tab completion stays the path that works everywhere.
 */
public final class BrigadierHook {

    private static final String[] EVENT_TYPES = {
            "com.destroystokyo.paper.event.brigadier.CommandRegisteredEvent",
            "io.papermc.paper.event.brigadier.CommandRegisteredEvent"
    };

    private static final AtomicBoolean LOGGED = new AtomicBoolean();

    private BrigadierHook() {
    }

    public static boolean install(Plugin plugin, Map<String, List<String>> literalsByLabel) {
        if (plugin == null || literalsByLabel == null) {
            return false;
        }
        boolean registered = listen(plugin, literalsByLabel);
        lifecycle(plugin, literalsByLabel);
        sync(plugin);
        return registered;
    }

    static boolean eventPresent() {
        return eventType() != null;
    }

    private static boolean listen(Plugin plugin, Map<String, List<String>> literalsByLabel) {
        Class<? extends Event> eventType = eventType();
        if (eventType == null) {
            return false;
        }
        try {
            plugin.getServer().getPluginManager().registerEvent(
                    eventType,
                    new Listener() {
                    },
                    EventPriority.NORMAL,
                    (listener, event) -> attach(plugin, event, literalsByLabel),
                    plugin
            );
            return true;
        } catch (Throwable ex) {
            plugin.getLogger().info("Brigadier suggestions were not attached: " + ex.getMessage());
            return false;
        }
    }

    private static void lifecycle(Plugin plugin, Map<String, List<String>> literalsByLabel) {
        try {
            Class<?> events = Class.forName("io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents");
            Object commands = events.getField("COMMANDS").get(null);
            if (commands == null) {
                return;
            }
            Object manager = plugin.getClass().getMethod("getLifecycleManager").invoke(plugin);
            Class<?> handler = Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEventHandler");
            InvocationHandler invocation = (proxy, method, args) -> {
                if ("run".equals(method.getName()) && args != null && args.length == 1 && args[0] != null) {
                    attachLifecycle(plugin, args[0], literalsByLabel);
                }
                return null;
            };
            Object proxy = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, invocation);
            for (Method method : manager.getClass().getMethods()) {
                if ("registerEventHandler".equals(method.getName()) && method.getParameterCount() == 2) {
                    method.invoke(manager, commands, proxy);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 1.20.4 has an empty lifecycle class. The registered-command event covers that build.
        }
    }

    private static void sync(Plugin plugin) {
        try {
            plugin.getServer().getClass().getMethod("syncCommands").invoke(plugin.getServer());
        } catch (Throwable ignored) {
            // Spigot and older Paper rebuild commands on their own.
        }
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

    private static void attachLifecycle(Plugin plugin, Object event, Map<String, List<String>> literalsByLabel) {
        try {
            Object registrar = event.getClass().getMethod("registrar").invoke(event);
            if (registrar == null) {
                return;
            }
            for (Method method : registrar.getClass().getMethods()) {
                if (!"getCommand".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Object node = method.invoke(registrar, "bffa");
                if (node != null) {
                    addChildren(plugin, node, literalsByLabel.get("bffa"));
                }
            }
        } catch (Throwable ignored) {
            // The command event is the fallback.
        }
    }

    private static void attach(Plugin plugin, Event event, Map<String, List<String>> literalsByLabel) {
        try {
            Object label = event.getClass().getMethod("getCommandLabel").invoke(event);
            if (!(label instanceof String command) || !literalsByLabel.containsKey(command)) {
                return;
            }
            Object node = event.getClass().getMethod("getLiteral").invoke(event);
            addChildren(plugin, node, literalsByLabel.get(command));
        } catch (Throwable ignored) {
            // Leave the Bukkit command node untouched.
        }
    }

    private static void addChildren(Plugin plugin, Object node, List<String> subs) throws Exception {
        if (node == null || subs == null || subs.isEmpty()) {
            return;
        }
        Method addChild = null;
        for (Method method : node.getClass().getMethods()) {
            if ("addChild".equals(method.getName()) && method.getParameterCount() == 1) {
                addChild = method;
                break;
            }
        }
        Class<?> literals = Class.forName("com.mojang.brigadier.builder.LiteralArgumentBuilder");
        Method literal = literals.getMethod("literal", String.class);
        boolean added = false;
        if (addChild != null) {
            for (String sub : subs) {
                Object builder = literal.invoke(null, sub);
                Object built = builder.getClass().getMethod("build").invoke(builder);
                addChild.invoke(node, built);
                added = true;
            }
        } else {
            Method then = null;
            for (Method method : node.getClass().getMethods()) {
                if ("then".equals(method.getName()) && method.getParameterCount() == 1) {
                    then = method;
                    break;
                }
            }
            if (then == null) {
                return;
            }
            for (String sub : subs) {
                then.invoke(node, literal.invoke(null, sub));
                added = true;
            }
        }
        if (added && LOGGED.compareAndSet(false, true)) {
            plugin.getLogger().info("Brigadier suggestions attached for /bffa");
        }
    }
}
