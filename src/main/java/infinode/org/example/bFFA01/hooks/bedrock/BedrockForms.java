package infinode.org.example.bFFA01.hooks.bedrock;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.Reflects;
import infinode.org.example.bFFA01.ui.menu.MenuView;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Cumulus simple forms for Bedrock players. Java players keep the chest GUI.
 * A form that cannot be built falls through to that GUI.
 */
public final class BedrockForms {

    private BedrockForms() {
    }

    /**
     * Renders the same {@link MenuView} the chest GUI uses. Returns false when the
     * player should see the chest instead.
     */
    public static boolean open(BFFA01 plugin, Player player, MenuView view, Consumer<String> click) {
        if (view == null || !wantsForm(plugin, player)) {
            return false;
        }
        List<MenuView.Control> buttons = view.clickable();
        if (buttons.isEmpty() && view.body().isEmpty()) {
            return false;
        }
        String[] labels = new String[buttons.size()];
        for (int i = 0; i < buttons.size(); i++) {
            String label = buttons.get(i).plain();
            labels[i] = label.isBlank() ? buttons.get(i).action() : label;
        }
        String content = body(view);
        return send(plugin, player, view.plainTitle(), content, labels, index -> {
            if (index < 0 || index >= buttons.size() || player == null || !player.isOnline()) {
                return;
            }
            String action = buttons.get(index).action();
            Runnable run = () -> {
                if (player.isOnline()) {
                    click.accept(action);
                }
            };
            ServerScheduler scheduler = plugin.scheduler();
            if (scheduler == null) {
                run.run();
            } else {
                scheduler.runOnEntity(player, run);
            }
        });
    }

    private static String body(MenuView view) {
        StringBuilder out = new StringBuilder();
        for (String line : view.body()) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.length() == 0 ? view.plainTitle() : out.toString();
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

}
