package infinode.org.example.bFFA01.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Map ids are written into Bukkit config paths and broadcast to every player.
 * Only a closed set of key characters is accepted so a name cannot create extra
 * path segments, break YAML boolean keys, or inject chat formatting.
 */
public final class MapNames {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final Set<String> RESERVED_KEYS = Set.of(
            "y", "n", "yes", "no", "on", "off", "true", "false", "null"
    );

    private MapNames() {
    }

    public static boolean isValid(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) {
            return false;
        }
        return !RESERVED_KEYS.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Display-only form of a map id already stored in config. Strips control
     * characters and legacy color markers so a hand-edited key cannot inject
     * extra chat lines or formatting.
     */
    public static String sanitizeLabel(String name) {
        if (name == null || name.isEmpty()) {
            return "Unknown";
        }
        StringBuilder out = new StringBuilder(Math.min(name.length(), 32));
        int limit = Math.min(name.length(), 32);
        for (int i = 0; i < limit; i++) {
            char c = name.charAt(i);
            if (c == '&' || c == '§' || Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
