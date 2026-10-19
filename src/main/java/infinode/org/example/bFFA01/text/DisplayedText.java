package infinode.org.example.bFFA01.text;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.PlaceholderResolver;
import infinode.org.example.bFFA01.hooks.mini.MiniPlaceholderBridge;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * One path for scoreboard, tab, boss bar, titles, and holograms.
 * Local tokens, BuildFFA placeholders, PlaceholderAPI, and MiniPlaceholders
 * are expanded before MiniMessage so a tag cannot be injected by a value.
 */
public final class DisplayedText {

    private DisplayedText() {
    }

    public static String mini(BFFA01 plugin, Player player, String template, Map<String, String> values) {
        return prepare(plugin, player, template, values);
    }

    public static String legacy(BFFA01 plugin, Player player, String template, Map<String, String> values) {
        return MiniText.render(prepare(plugin, player, template, values), Map.of());
    }

    static String prepare(String template, Map<String, String> values, PlaceholderResolver resolver,
                          java.util.function.UnaryOperator<String> theme,
                          java.util.function.BiFunction<Player, String, String> mini,
                          Player player) {
        String local = MiniText.substitute(template == null ? "" : template, values);
        String expanded = local;
        if (resolver != null) {
            try {
                expanded = resolver.apply(player, local);
            } catch (Throwable ex) {
                expanded = local;
            }
        }
        if (expanded == null) {
            expanded = "";
        }
        String themed = theme == null ? expanded : theme.apply(expanded);
        if (mini == null) {
            return themed == null ? "" : themed;
        }
        try {
            String resolved = mini.apply(player, themed);
            return resolved == null ? themed : resolved;
        } catch (Throwable ex) {
            return themed == null ? "" : themed;
        }
    }

    private static String prepare(BFFA01 plugin, Player player, String template, Map<String, String> values) {
        return prepare(template, values, resolver(plugin), text -> theme(plugin, text), (viewer, text) -> mini(plugin, viewer, text), player);
    }

    private static PlaceholderResolver resolver(BFFA01 plugin) {
        try {
            if (plugin == null || plugin.services() == null) {
                return PlaceholderResolver.identity();
            }
            return plugin.services().get(PlaceholderResolver.class).orElseGet(PlaceholderResolver::identity);
        } catch (Throwable ignored) {
            return PlaceholderResolver.identity();
        }
    }

    private static String theme(BFFA01 plugin, String text) {
        try {
            Messages messages = plugin == null ? null : plugin.getMessages();
            if (messages == null) {
                return ThemeText.apply(text, ThemeText.defaultColors(), ThemeText.defaultIcons());
            }
            return messages.theme(text);
        } catch (Throwable ignored) {
            return text == null ? "" : text;
        }
    }

    private static String mini(BFFA01 plugin, Player player, String text) {
        try {
            if (plugin == null || plugin.services() == null || text == null || text.indexOf('<') < 0) {
                return text;
            }
            MiniPlaceholderBridge bridge = plugin.services().get(MiniPlaceholderBridge.class).orElse(null);
            return bridge == null ? text : bridge.expand(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
