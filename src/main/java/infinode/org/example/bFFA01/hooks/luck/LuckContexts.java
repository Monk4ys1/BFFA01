package infinode.org.example.bFFA01.hooks.luck;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * LuckPerms context values stay low-cardinality. Streaks are buckets, not the raw count.
 */
public final class LuckContexts {

    private LuckContexts() {
    }

    public static Map<String, String> of(String map, int streak, boolean bedrock) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("bffa-map", mapKey(map));
        values.put("bffa-streak", bucket(streak));
        values.put("bffa-bedrock", bedrock ? "true" : "false");
        return values;
    }

    public static String bucket(int streak) {
        if (streak <= 0) {
            return "0";
        }
        if (streak < 5) {
            return "1-4";
        }
        if (streak < 10) {
            return "5-9";
        }
        return "10+";
    }

    public static String mapKey(String map) {
        if (map == null || map.isBlank()) {
            return "none";
        }
        StringBuilder out = new StringBuilder(Math.min(map.length(), 32));
        for (int i = 0; i < map.length() && out.length() < 32; i++) {
            char c = Character.toLowerCase(map.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.isEmpty() ? "none" : out.toString();
    }
}
