package infinode.org.example.bFFA01.compat;

import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What kind of server loaded the plugin. Detection uses class presence only.
 */
public final class ServerRuntime {

    public enum Kind {
        FOLIA,
        PURPUR,
        PAPER,
        SPIGOT
    }

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private final Kind kind;
    private final String version;

    public ServerRuntime(Kind kind, String version) {
        this.kind = kind == null ? Kind.SPIGOT : kind;
        this.version = version == null ? "" : version;
    }

    public Kind kind() {
        return kind;
    }

    public String version() {
        return version;
    }

    public String describe() {
        String label = switch (kind) {
            case FOLIA -> "Folia";
            case PURPUR -> "Purpur";
            case PAPER -> "Paper";
            case SPIGOT -> "Spigot";
        };
        return version.isEmpty() ? label : label + " " + version;
    }

    public static Kind detect() {
        return detect(ServerRuntime::classExists);
    }

    static Kind detect(Predicate<String> classExists) {
        if (classExists.test("io.papermc.paper.threadedregions.RegionizedServer")) {
            return Kind.FOLIA;
        }
        if (classExists.test("org.purpurmc.purpur.PurpurConfig")) {
            return Kind.PURPUR;
        }
        if (classExists.test("io.papermc.paper.ServerBuildInfo")
                || classExists.test("com.destroystokyo.paper.PaperConfig")) {
            return Kind.PAPER;
        }
        return Kind.SPIGOT;
    }

    public static int[] parseVersion(String version) {
        if (version == null) {
            return new int[] {0, 0, 0};
        }
        Matcher matcher = VERSION.matcher(version);
        if (!matcher.find()) {
            return new int[] {0, 0, 0};
        }
        return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
        };
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, ServerRuntime.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
