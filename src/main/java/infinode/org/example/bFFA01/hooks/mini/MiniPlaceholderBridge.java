package infinode.org.example.bFFA01.hooks.mini;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.Reflects;
import infinode.org.example.bFFA01.hooks.papi.BffaTokens;
import infinode.org.example.bFFA01.hooks.papi.PluginStatSource;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Registers a MiniPlaceholders expansion so {@code <bffa_kills>} resolves in other plugins.
 * PlaceholderAPI's {@code <papi:bffa_kills>} path still works when this API shape changes.
 */
public final class MiniPlaceholderBridge {

    private static final String[] AUDIENCE = {
            "kills", "deaths", "kd", "coins", "streak", "rank", "prefix", "bedrock", "protocol", "client", "vault"
    };
    private static final String[] GLOBAL = {"map", "kit", "online", "top_name", "top_kills", "top_kd"};

    private volatile Object expansion;

    public void enable(BFFA01 plugin, Plugin mini) {
        ClassLoader loader = mini.getClass().getClassLoader();
        Class<?> expansionType = Reflects.load(loader, "io.github.miniplaceholders.api.Expansion");
        if (expansionType == null) {
            throw new IllegalStateException("MiniPlaceholders Expansion API is missing");
        }
        Object builder;
        try {
            builder = expansionType.getMethod("builder", String.class).invoke(null, "bffa");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("MiniPlaceholders Expansion.builder is missing", ex);
        }
        Reflects.invoke(builder, "author", "BFFA01");
        Reflects.invoke(builder, "version", plugin.getDescription().getVersion());
        for (String key : AUDIENCE) {
            if (!add(builder, "audiencePlaceholder", key, loader, (queue) ->
                    BffaTokens.resolve(new PluginStatSource(plugin), viewer(queue), key))) {
                throw new IllegalStateException("MiniPlaceholders audience placeholders were not accepted");
            }
        }
        for (String key : GLOBAL) {
            if (!add(builder, "globalPlaceholder", key, loader, (queue) -> {
                String argument = argument(queue);
                String params = argument == null ? key : key + "_" + argument;
                String resolved = BffaTokens.resolve(new PluginStatSource(plugin), null, params);
                return resolved == null ? "" : resolved;
            })) {
                throw new IllegalStateException("MiniPlaceholders global placeholder " + key + " was not accepted");
            }
        }
        Object built = Reflects.call(builder, "build");
        if (built == null || !Reflects.invoke(built, "register")) {
            throw new IllegalStateException("MiniPlaceholders expansion did not register");
        }
        this.expansion = built;
    }

    public void disable() {
        Object current = expansion;
        expansion = null;
        Reflects.invoke(current, "unregister");
    }

    private boolean add(Object builder, String methodName, String key, ClassLoader loader, java.util.function.Function<Object[], String> value) {
        for (Method method : builder.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            try {
                if (params.length == 3 && params[0] == Class.class && params[1] == String.class && params[2].isInterface()) {
                    method.invoke(builder, Player.class, key, proxy(loader, params[2], value));
                    return true;
                }
                if (params.length == 2 && params[0] == String.class && params[1].isInterface()) {
                    method.invoke(builder, key, proxy(loader, params[1], value));
                    return true;
                }
            } catch (Throwable ignored) {
                // Try the next overload.
            }
        }
        return false;
    }

    private Object proxy(ClassLoader loader, Class<?> type, java.util.function.Function<Object[], String> value) {
        return Proxy.newProxyInstance(loader, new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "bffa";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                };
            }
            if (method.isDefault()) {
                return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
            }
            return tag(loader, value.apply(args));
        });
    }

    private static Object tag(ClassLoader loader, String text) throws ReflectiveOperationException {
        Class<?> component = Class.forName("net.kyori.adventure.text.Component", true, loader);
        Class<?> tag = Class.forName("net.kyori.adventure.text.minimessage.tag.Tag", true, loader);
        Object body = component.getMethod("text", String.class).invoke(null, text == null ? "" : text);
        try {
            return tag.getMethod("selfClosingInserting", component).invoke(null, body);
        } catch (NoSuchMethodException ex) {
            return tag.getMethod("inserting", component).invoke(null, body);
        }
    }

    private static UUID viewer(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Player player) {
                return player.getUniqueId();
            }
            Object id = Reflects.call(arg, "getUniqueId");
            if (id instanceof UUID uuid) {
                return uuid;
            }
        }
        return null;
    }

    private static String argument(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            Object ready = Reflects.call(arg, "hasNext");
            if (!(ready instanceof Boolean next) || !next) {
                continue;
            }
            Object popped = Reflects.call(arg, "pop");
            Object value = Reflects.call(popped, "value");
            if (value != null) {
                return value.toString();
            }
            if (popped != null) {
                return popped.toString();
            }
        }
        return null;
    }
}
