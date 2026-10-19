package infinode.org.example.bFFA01.hooks.luck;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.Reflects;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockPlayers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Registers a LuckPerms context calculator and reads prefixes. The calculator only
 * reads the current map name, the streak bucket, and the Bedrock flag.
 */
public final class LuckPermsHook {

    private final Prefixes prefixes;
    private volatile Object contextManager;
    private volatile Object calculator;
    private volatile Object api;

    public LuckPermsHook(Prefixes prefixes) {
        this.prefixes = prefixes;
    }

    public void enable(BFFA01 plugin, Plugin luckPerms, Logger logger) {
        Class<?> calculatorType = Reflects.load(luckPerms.getClass().getClassLoader(), "net.luckperms.api.context.ContextCalculator");
        Class<?> provider = Reflects.load(luckPerms.getClass().getClassLoader(), "net.luckperms.api.LuckPermsProvider");
        if (calculatorType == null || provider == null) {
            throw new IllegalStateException("LuckPerms API is not visible");
        }
        Object luck = Reflects.callStatic(provider, "get");
        if (luck == null) {
            throw new IllegalStateException("LuckPermsProvider.get() returned null");
        }
        Object manager = Reflects.call(luck, "getContextManager");
        if (manager == null) {
            throw new IllegalStateException("LuckPerms context manager is missing");
        }
        Object proxy = Proxy.newProxyInstance(calculatorType.getClassLoader(), new Class<?>[]{calculatorType}, handler(plugin));
        if (!Reflects.invoke(manager, "registerCalculator", proxy)) {
            throw new IllegalStateException("Could not register the LuckPerms context calculator");
        }
        this.api = luck;
        this.contextManager = manager;
        this.calculator = proxy;
        prefixes.luck(player -> prefix(player));
    }

    public void disable() {
        Object manager = contextManager;
        Object proxy = calculator;
        if (manager != null && proxy != null) {
            Reflects.call(manager, "unregisterCalculator", proxy);
        }
        contextManager = null;
        calculator = null;
        api = null;
        prefixes.luck(null);
    }

    public void signal(Player player) {
        if (player == null || contextManager == null) {
            return;
        }
        Reflects.call(contextManager, "signalContextUpdate", player);
    }

    private String prefix(Player player) {
        Object adapter = Reflects.call(api, "getPlayerAdapter", Player.class);
        Object user = Reflects.call(adapter, "getUser", player);
        Object cached = Reflects.call(user, "getCachedData");
        Object meta = Reflects.call(cached, "getMetaData");
        Object value = Reflects.call(meta, "getPrefix");
        return value instanceof String text ? text : "";
    }

    private InvocationHandler handler(BFFA01 plugin) {
        return (proxy, method, args) -> {
            try {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "BFFA01 contexts";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> null;
                };
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if ("calculate".equals(method.getName()) && args != null && args.length >= 2 && args[0] instanceof Player player) {
                BedrockPlayers bedrock = null;
                try {
                    if (plugin.services() != null) {
                        bedrock = plugin.services().get(BedrockPlayers.class).orElse(null);
                    }
                } catch (Throwable ignored) {
                    bedrock = null;
                }
                String map = "";
                int streak = 0;
                try {
                    if (plugin.getMapManager() != null) {
                        map = plugin.getMapManager().getCurrentMap();
                    }
                    if (plugin.getKillstreakManager() != null) {
                        streak = plugin.getKillstreakManager().getStreak(player);
                    }
                } catch (Throwable ignored) {
                    map = "";
                }
                boolean floodgate = bedrock != null && bedrock.bedrock(player.getUniqueId());
                for (Map.Entry<String, String> entry : LuckContexts.of(map, streak, floodgate).entrySet()) {
                    Reflects.call(args[1], "accept", entry.getKey(), entry.getValue());
                }
            }
            return null;
            } catch (Throwable ignored) {
                return null;
            }
        };
    }
}
