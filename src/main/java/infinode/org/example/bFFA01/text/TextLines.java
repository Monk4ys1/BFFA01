package infinode.org.example.bFFA01.text;

import infinode.org.example.bFFA01.BFFA01;

import java.util.Map;

/**
 * Renders a catalog line, or the English fallback when messages are not loaded yet.
 */
public final class TextLines {

    private TextLines() {
    }

    public static String render(BFFA01 plugin, String key, String fallback, Map<String, String> placeholders) {
        Messages messages = messages(plugin);
        if (messages != null) {
            return messages.render(key, placeholders == null ? Map.of() : placeholders);
        }
        String raw = fallback;
        try {
            if (plugin != null && plugin.getConfig() != null) {
                String configured = plugin.getConfig().getString("messages." + key, fallback);
                if (configured != null) {
                    raw = configured;
                }
            }
        } catch (Throwable ignored) {
            raw = fallback;
        }
        return MiniText.render(raw == null ? "" : raw, placeholders);
    }

    public static String prefixed(BFFA01 plugin, String key, String fallback, Map<String, String> placeholders) {
        return render(plugin, "prefix", "&8[&bBuildFFA&8] ", Map.of())
                + render(plugin, key, fallback, placeholders);
    }

    private static Messages messages(BFFA01 plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            return plugin.getMessages();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
