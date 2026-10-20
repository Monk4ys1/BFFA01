package infinode.org.example.bFFA01.ui.prefs;

import java.util.Locale;

/**
 * Compact preference row stored beside the player in SQL.
 * Order: scoreboard, bossbar, actionbar, damage, effect, welcomed.
 */
public final class PrefCodec {

    public record Values(boolean scoreboard, boolean bossbar, boolean actionbar, boolean damage, String effect, boolean welcomed) {
    }

    private PrefCodec() {
    }

    public static String encode(Values values) {
        if (values == null) {
            return "";
        }
        return bit(values.scoreboard) + "," + bit(values.bossbar) + "," + bit(values.actionbar) + ","
                + bit(values.damage) + "," + values.effect + "," + bit(values.welcomed);
    }

    public static Values decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(",", -1);
        if (parts.length != 6) {
            return null;
        }
        String effect = effect(parts[4]);
        if (effect == null) {
            return null;
        }
        return new Values(bit(parts[0]), bit(parts[1]), bit(parts[2]), bit(parts[3]), effect, bit(parts[5]));
    }

    public static String effect(String raw) {
        if (raw == null || raw.isBlank()) {
            return "totem";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_-]{1,24}")) {
            return null;
        }
        return value;
    }

    private static String bit(boolean value) {
        return value ? "1" : "0";
    }

    private static boolean bit(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
