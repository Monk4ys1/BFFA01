package infinode.org.example.bFFA01.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces palette and icon tokens before MiniMessage runs.
 * A color token becomes a safe {@code #rrggbb} value, so a config file cannot open tags.
 */
public final class ThemeText {

    private static final Pattern COLOR = Pattern.compile("%color_([A-Za-z0-9_-]+)%");
    private static final Pattern ICON = Pattern.compile("%icon_([A-Za-z0-9_-]+)%");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");

    private static final Map<String, String> DEFAULT_COLORS = Map.of(
            "primary", "#7ad7ff",
            "secondary", "#6e8bff",
            "accent", "#f6c453",
            "success", "#7dffb3",
            "danger", "#ff6b7a",
            "muted", "#9aa4b2",
            "soft", "#c9b6ff",
            "warm", "#ff8b6a"
    );

    private static final Map<String, String> DEFAULT_ICONS = icons();

    private ThemeText() {
    }

    public static Map<String, String> defaultColors() {
        return DEFAULT_COLORS;
    }

    public static Map<String, String> defaultIcons() {
        return DEFAULT_ICONS;
    }

    public static String apply(String template, Map<String, String> colors, Map<String, String> icons) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, String> palette = colors == null || colors.isEmpty() ? DEFAULT_COLORS : colors;
        Map<String, String> marks = icons == null || icons.isEmpty() ? DEFAULT_ICONS : icons;
        Matcher colorsMatcher = COLOR.matcher(template);
        StringBuilder colorPass = new StringBuilder(template.length() + 16);
        while (colorsMatcher.find()) {
            String hex = palette.get(colorsMatcher.group(1).toLowerCase(Locale.ROOT));
            colorsMatcher.appendReplacement(colorPass, Matcher.quoteReplacement(hex == null ? colorsMatcher.group() : hex));
        }
        colorsMatcher.appendTail(colorPass);
        Matcher iconsMatcher = ICON.matcher(colorPass);
        StringBuilder out = new StringBuilder(colorPass.length());
        while (iconsMatcher.find()) {
            String icon = marks.get(iconsMatcher.group(1).toLowerCase(Locale.ROOT));
            iconsMatcher.appendReplacement(out, Matcher.quoteReplacement(icon == null ? iconsMatcher.group() : icon));
        }
        iconsMatcher.appendTail(out);
        return out.toString();
    }

    public static String hex(String raw, String fallback) {
        String safeFallback = fallback == null ? "#ffffff" : fallback;
        if (raw == null) {
            return safeFallback;
        }
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!HEX.matcher(value).matches()) {
            return safeFallback;
        }
        return "#" + value.toLowerCase(Locale.ROOT);
    }

    public static String icon(String raw, String fallback) {
        String safeFallback = fallback == null ? "" : fallback;
        if (raw == null || raw.isBlank()) {
            return safeFallback;
        }
        String value = raw.trim();
        if (value.length() > 4) {
            value = value.substring(0, 4);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>' || c == '&' || c == '§' || c == '%' || Character.isISOControl(c)) {
                return safeFallback;
            }
        }
        return value;
    }

    public static Map<String, String> colors(Map<String, String> overrides) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_COLORS);
        if (overrides != null) {
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!DEFAULT_COLORS.containsKey(key) && !key.matches("[a-z0-9_-]{1,24}")) {
                    continue;
                }
                merged.put(key, hex(entry.getValue(), merged.getOrDefault(key, "#ffffff")));
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    public static Map<String, String> icons(Map<String, String> overrides) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_ICONS);
        if (overrides != null) {
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!key.matches("[a-z0-9_-]{1,24}")) {
                    continue;
                }
                merged.put(key, icon(entry.getValue(), merged.getOrDefault(key, "")));
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    private static Map<String, String> icons() {
        Map<String, String> icons = new LinkedHashMap<>();
        icons.put("map", "\u25c7");
        icons.put("kills", "\u2694");
        icons.put("deaths", "\u2620");
        icons.put("coins", "\u2726");
        icons.put("streak", "\u2605");
        icons.put("time", "\u25f7");
        icons.put("players", "\u263a");
        icons.put("shop", "\u25c6");
        icons.put("kit", "\u25a3");
        icons.put("stats", "\u265b");
        icons.put("settings", "\u2699");
        icons.put("vote", "\u25b2");
        icons.put("hit", "\u2738");
        return Collections.unmodifiableMap(icons);
    }
}
