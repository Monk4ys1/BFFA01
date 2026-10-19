package infinode.org.example.bFFA01.hooks.bedrock;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.Reflects;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.shop.ShopPurchase;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.UUID;

/**
 * Cumulus simple forms for Bedrock players. Java players keep the chest GUI.
 * A form that cannot be built falls through to that GUI.
 */
public final class BedrockForms {

    private BedrockForms() {
    }

    public static boolean openStats(BFFA01 plugin, Player player) {
        if (!wantsForm(plugin, player)) {
            return false;
        }
        int kills = plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager().getDeaths(player.getUniqueId());
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String content = "Kills: " + kills
                + "\nDeaths: " + deaths
                + "\nK/D: " + String.format(Locale.US, "%.2f", kd)
                + "\nCoins: " + plugin.getDataManager().getCoins(player.getUniqueId())
                + "\nStreak: " + plugin.getKillstreakManager().getStreak(player)
                + "\nMap: " + map(plugin);
        return send(plugin, player, "BuildFFA", content, new String[]{"Close"}, index -> {
        });
    }

    public static boolean openShop(BFFA01 plugin, Player player) {
        if (!wantsForm(plugin, player)) {
            return false;
        }
        ShopOffer[] offers = ShopOffer.values();
        String[] buttons = new String[offers.length];
        for (int i = 0; i < offers.length; i++) {
            buttons[i] = offers[i].id() + " (" + offers[i].cost() + ")";
        }
        String content = "Coins: " + plugin.getDataManager().getCoins(player.getUniqueId());
        return send(plugin, player, "BuildFFA Shop", content, buttons, index -> {
            if (index < 0 || index >= offers.length || !player.isOnline()) {
                return;
            }
            Runnable buy = () -> ShopPurchase.buy(plugin, player, offers[index]);
            ServerScheduler scheduler = plugin.scheduler();
            if (scheduler == null) {
                buy.run();
            } else {
                scheduler.runOnEntity(player, buy);
            }
        });
    }

    private static boolean wantsForm(BFFA01 plugin, Player player) {
        if (plugin == null || player == null || !IntegrationSettings.forms(IntegrationSettings.config(plugin))) {
            return false;
        }
        try {
            if (plugin.services() == null || plugin.integrations() == null || !plugin.integrations().isActive("floodgate")) {
                return false;
            }
            BedrockPlayers bedrock = plugin.services().get(BedrockPlayers.class).orElse(null);
            return bedrock != null && bedrock.bedrock(player.getUniqueId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean send(BFFA01 plugin, Player player, String title, String content, String[] buttons, java.util.function.IntConsumer click) {
        try {
            Plugin floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
            if (floodgate == null) {
                return false;
            }
            ClassLoader loader = floodgate.getClass().getClassLoader();
            Class<?> formType = Reflects.load(loader, "org.geysermc.cumulus.form.SimpleForm");
            Class<?> apiType = Reflects.load(loader, "org.geysermc.floodgate.api.FloodgateApi");
            if (formType == null || apiType == null) {
                return false;
            }
            Object builder = formType.getMethod("builder").invoke(null);
            Reflects.invoke(builder, "title", title);
            Reflects.invoke(builder, "content", content);
            for (String button : buttons) {
                Reflects.invoke(builder, "button", button);
            }
            Class<?> handler = handlerType(builder);
            if (handler != null) {
                Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{handler}, (proxyObject, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return objectCall(proxyObject, method.getName(), args);
                    }
                    if (method.isDefault()) {
                        return java.lang.reflect.InvocationHandler.invokeDefault(proxyObject, method, args);
                    }
                    int index = clicked(args);
                    if (index >= 0) {
                        click.accept(index);
                    }
                    return null;
                });
                if (!Reflects.invoke(builder, "validResultHandler", proxy)) {
                    Reflects.invoke(builder, "responseHandler", proxy);
                }
            }
            Object form = Reflects.call(builder, "build");
            if (form == null) {
                form = Reflects.call(builder, "build");
            }
            if (form == null) {
                return false;
            }
            Object api = Reflects.callStatic(apiType, "getInstance");
            UUID id = player.getUniqueId();
            return Reflects.invoke(api, "sendForm", id, form);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Bedrock form was not sent: " + ex.getMessage());
            return false;
        }
    }

    private static Class<?> handlerType(Object builder) {
        for (java.lang.reflect.Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() == 1
                    && ("validResultHandler".equals(method.getName()) || "responseHandler".equals(method.getName()))
                    && method.getParameterTypes()[0].isInterface()) {
                return method.getParameterTypes()[0];
            }
        }
        return null;
    }

    private static int clicked(Object[] args) {
        if (args == null) {
            return -1;
        }
        for (Object arg : args) {
            Object id = Reflects.call(arg, "clickedButtonId");
            if (id instanceof Number number) {
                return number.intValue();
            }
        }
        return -1;
    }

    private static Object objectCall(Object proxy, String name, Object[] args) {
        return switch (name) {
            case "toString" -> "BFFA01 form";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }

    private static String map(BFFA01 plugin) {
        try {
            String map = plugin.getMapManager().getCurrentMap();
            return map == null ? "-" : map;
        } catch (Throwable ignored) {
            return "-";
        }
    }
}
