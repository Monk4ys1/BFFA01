package infinode.org.example.bFFA01.ui.scoreboard;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a sidebar line still matches the values already drawn.
 */
public final class SidebarCache {

    private SidebarCache() {
    }

    public static boolean same(Map<String, String> previous, Map<String, String> next) {
        if (previous == next) {
            return true;
        }
        if (previous == null || next == null) {
            return false;
        }
        return previous.equals(next);
    }

    /**
     * A line is redrawn when a changed key appears as {@code %key%}, or when the
     * template contains a {@code %} token that is not one of the known keys.
     */
    public static boolean lineAffected(String template, Map<String, String> previous, Map<String, String> next) {
        if (template == null || template.isEmpty()) {
            return false;
        }
        if (previous == null || next == null) {
            return true;
        }
        Set<String> known = new HashSet<>(previous.keySet());
        known.addAll(next.keySet());
        int cursor = 0;
        while (cursor < template.length()) {
            int start = template.indexOf('%', cursor);
            if (start < 0) {
                return false;
            }
            int end = template.indexOf('%', start + 1);
            if (end < 0) {
                return true;
            }
            String token = template.substring(start + 1, end);
            if (!known.contains(token)) {
                return true;
            }
            String before = previous.get(token);
            String after = next.get(token);
            if (before == null ? after != null : !before.equals(after)) {
                return true;
            }
            cursor = end + 1;
        }
        return false;
    }
}
