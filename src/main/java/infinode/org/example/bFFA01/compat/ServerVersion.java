package infinode.org.example.bFFA01.compat;

import org.bukkit.Bukkit;

/**
 * Parses the running server version once so feature checks never have to
 * string-match {@code Bukkit.getBukkitVersion()} again.
 *
 * <p>The plugin is compiled against the 1.20.1 API and supports every release
 * from {@value #MIN_MINOR}.{@value #MIN_PATCH} upwards; anything that changed
 * in later releases is resolved reflectively in {@link Compat}.
 */
public final class ServerVersion {

    /** Lowest supported minor version (1.<b>20</b>.1). */
    public static final int MIN_MINOR = 20;
    /** Lowest supported patch version (1.20.<b>1</b>). */
    public static final int MIN_PATCH = 1;

    private static int major = 1;
    private static int minor = 0;
    private static int patch = 0;
    private static String raw = "unknown";
    private static boolean initialised;

    private ServerVersion() {
    }

    private static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        try {
            raw = Bukkit.getBukkitVersion();
        } catch (Throwable ignored) {
            // Bukkit is not available (unit test / stub environment).
            return;
        }
        // "1.20.1-R0.1-SNAPSHOT" -> "1.20.1"
        String numeric = raw.split("-", 2)[0];
        String[] parts = numeric.split("\\.");
        major = parseOrZero(parts, 0);
        minor = parseOrZero(parts, 1);
        patch = parseOrZero(parts, 2);
    }

    private static int parseOrZero(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int major() {
        init();
        return major;
    }

    public static int minor() {
        init();
        return minor;
    }

    public static int patch() {
        init();
        return patch;
    }

    /** Raw version string, e.g. {@code 1.21.4-R0.1-SNAPSHOT}. */
    public static String raw() {
        init();
        return raw;
    }

    /** Short version string, e.g. {@code 1.21.4}. */
    public static String shortVersion() {
        init();
        return major + "." + minor + "." + patch;
    }

    /** {@code true} when the server is at least 1.{@code minMinor}.{@code minPatch}. */
    public static boolean atLeast(int minMinor, int minPatch) {
        init();
        if (major != 1) {
            return major > 1;
        }
        if (minor != minMinor) {
            return minor > minMinor;
        }
        return patch >= minPatch;
    }

    /** {@code true} when the server is new enough to run this plugin. */
    public static boolean isSupported() {
        return atLeast(MIN_MINOR, MIN_PATCH);
    }
}
