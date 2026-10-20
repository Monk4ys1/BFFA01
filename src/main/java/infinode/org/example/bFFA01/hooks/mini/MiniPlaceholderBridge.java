package infinode.org.example.bFFA01.hooks.mini;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.Reflects;
import infinode.org.example.bFFA01.hooks.papi.BffaTokens;
import infinode.org.example.bFFA01.hooks.papi.PluginStatSource;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registers a MiniPlaceholders expansion so {@code <bffa_kills>} resolves in other plugins.
 * PlaceholderAPI's {@code <papi:bffa_kills>} path still works when this API shape changes.
 */
public final class MiniPlaceholderBridge {

    private static final String[] AUDIENCE = {
            "kills", "deaths", "kd", "coins", "streak", "rank", "prefix", "bedrock", "protocol", "client", "vault",
            "damage", "damage_taken", "life_damage", "life_taken", "assists"
    };
    private static final String[] GLOBAL = {"map", "kit", "online", "top_name", "top_kills", "top_kd", "top_damage", "top_damage_name"};

    private static final Pattern TAG = Pattern.compile("<([A-Za-z][A-Za-z0-9_]*)(?::([^>]*))?>");
    private static final Set<String> FORMAT = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray",
            "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "bold", "italic", "underlined",
            "strikethrough", "obfuscated", "reset", "gradient", "rainbow", "click", "hover", "insertion", "font",
            "transition", "newline", "key", "lang", "selector", "score", "nbt", "shadow", "color"
    );

    private volatile Object expansion;
    private volatile ClassLoader loader;
    private volatile boolean ready;

    public void enable(BFFA01 plugin, Plugin mini) {
        ClassLoader loader = mini.getClass().getClassLoader();
        this.loader = loader;
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
        this.ready = true;
    }

    public void disable() {
        ready = false;
        Object current = expansion;
        expansion = null;
        loader = null;
        Reflects.invoke(current, "unregister");
    }

    /**
     * Replaces MiniPlaceholders tags with plain text and leaves MiniMessage formatting tags alone.
     */
    public String expand(Player player, String text) {
        if (!ready || loader == null || text == null || text.indexOf('<') < 0) {
            return text;
        }
        Matcher matcher = TAG.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        boolean changed = false;
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            if (FORMAT.contains(name) || name.startsWith("bffa")) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String plain = resolve(player, matcher.group());
            if (plain == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                changed = true;
                matcher.appendReplacement(out, Matcher.quoteReplacement(plain));
            }
        }
        if (!changed) {
            return text;
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String resolve(Player player, String snippet) {
        ClassLoader current = loader;
        if (current == null) {
            return null;
        }
        try {
            Class<?> api = Class.forName("io.github.miniplaceholders.api.MiniPlaceholders", true, current);
            Class<?> audience = Class.forName("net.kyori.adventure.audience.Audience", true, current);
            Object resolver = null;
            if (player != null && audience.isInstance(player)) {
                for (Method method : api.getMethods()) {
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(audience)
                            && (method.getName().equals("getAudiencePlaceholders") || method.getName().equals("audiencePlaceholders"))) {
                        resolver = method.invoke(null, player);
                        break;
                    }
                }
            }
            if (resolver == null) {
                for (String name : new String[]{"getGlobalPlaceholders", "globalPlaceholders"}) {
                    try {
                        resolver = api.getMethod(name).invoke(null);
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // Try the other name.
                    }
                }
            }
            if (resolver == null) {
                return null;
            }
            Class<?> mini = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage", true, current);
            Object parser = mini.getMethod("miniMessage").invoke(null);
            Object component = null;
            for (Method method : parser.getClass().getMethods()) {
                if (!"deserialize".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                if (method.getParameterTypes()[0] != String.class) {
                    continue;
                }
                if (!method.getParameterTypes()[1].isInstance(resolver)) {
                    continue;
                }
                component = method.invoke(parser, snippet, resolver);
                break;
            }
            if (component == null) {
                return null;
            }
            Class<?> plainType = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer", true, current);
            Object plain = plainType.getMethod("plainText").invoke(null);
            String value = null;
            for (Method method : plain.getClass().getMethods()) {
                if (!"serialize".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isInstance(component)) {
                    continue;
                }
                Object serialized = method.invoke(plain, component);
                if (serialized instanceof String text) {
                    value = text;
                    break;
                }
            }
            if (value == null || value.isBlank() || value.contains("<")) {
                return null;
            }
            return value.length() > 64 ? value.substring(0, 64) : value;
        } catch (Throwable ex) {
            return null;
        }
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
