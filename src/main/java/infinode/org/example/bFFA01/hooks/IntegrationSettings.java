package infinode.org.example.bFFA01.hooks;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the {@code integrations} section. Missing keys keep the documented defaults,
 * so an older config.yml does not need a rewrite.
 */
public final class IntegrationSettings {

    /** Absent key stays at zero so an older config.yml does not start paying coins. */
    public static final int DEFAULT_KILL_COINS = 0;
    public static final List<Integer> DEFAULT_MILESTONES = List.of(5, 10, 15, 20, 25, 50);

    private IntegrationSettings() {
    }

    public static boolean enabled(FileConfiguration config, String id, boolean defaultValue) {
        if (config == null || id == null) {
            return defaultValue;
        }
        return config.getBoolean("integrations." + id + ".enabled", defaultValue);
    }

    public static String shopMode(FileConfiguration config) {
        return mode(config, "integrations.vault.shop", "internal", "vault");
    }

    public static String killMode(FileConfiguration config) {
        String raw = config == null ? null : config.getString("integrations.vault.kill-reward", "internal");
        if (raw == null) {
            return "internal";
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "vault" -> "vault";
            case "both" -> "both";
            default -> "internal";
        };
    }

    public static int killAmount(FileConfiguration config) {
        int amount = config == null ? DEFAULT_KILL_COINS : config.getInt("integrations.vault.kill-amount", DEFAULT_KILL_COINS);
        if (amount < 0) {
            return 0;
        }
        return Math.min(amount, 1_000_000);
    }

    public static boolean forms(FileConfiguration config) {
        if (config == null) {
            return true;
        }
        return config.getBoolean("integrations.floodgate.forms", true);
    }

    public static String hologramBackend(FileConfiguration config) {
        String raw = config == null ? null : config.getString("holograms.backend", "auto");
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    public static String hologramId(FileConfiguration config) {
        String raw = config == null ? null : config.getString("holograms.id", "bffa-top");
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{1,32}")) {
            return "bffa-top";
        }
        return raw;
    }

    public static String kitName(FileConfiguration config) {
        String raw = config == null ? null : config.getString("kit.name", "default");
        if (raw == null || raw.isBlank()) {
            return "default";
        }
        String trimmed = raw.trim();
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    public static String arenaRegion(FileConfiguration config, String mapName) {
        if (config != null && mapName != null && !mapName.isBlank()) {
            String perMap = config.getString("maps." + mapName + ".region", "");
            if (perMap != null && !perMap.isBlank()) {
                return perMap.trim();
            }
        }
        if (config == null) {
            return "";
        }
        String global = config.getString("integrations.worldguard.arena-region", "");
        return global == null ? "" : global.trim();
    }

    public static boolean denyBuildOutside(FileConfiguration config) {
        return config != null && config.getBoolean("integrations.worldguard.deny-build-outside", false);
    }

    public static boolean denyPvpOutside(FileConfiguration config) {
        return config != null && config.getBoolean("integrations.worldguard.deny-pvp-outside", false);
    }

    public static boolean worldGuardFlags(FileConfiguration config) {
        return config == null || config.getBoolean("integrations.worldguard.flags", true);
    }

    public static int metricsId(FileConfiguration config) {
        if (config == null) {
            return 0;
        }
        return Math.max(0, config.getInt("integrations.metrics.plugin-id", 0));
    }

    public static Discord discord(FileConfiguration config) {
        if (config == null) {
            return Discord.defaults();
        }
        List<Integer> milestones = new ArrayList<>();
        for (int value : config.getIntegerList("integrations.discord.milestones")) {
            if (value > 0 && value < 10_000) {
                milestones.add(value);
            }
        }
        if (milestones.isEmpty() && !config.isSet("integrations.discord.milestones")) {
            milestones.addAll(DEFAULT_MILESTONES);
        }
        int rate = config.getInt("integrations.discord.rate-limit", 30);
        return new Discord(
                config.getBoolean("integrations.discord.enabled", false),
                safe(config.getString("integrations.discord.url", "")),
                blank(config.getString("integrations.discord.username", "BuildFFA"), "BuildFFA"),
                config.getBoolean("integrations.discord.kills", true),
                config.getBoolean("integrations.discord.deaths", false),
                config.getBoolean("integrations.discord.streaks", true),
                config.getBoolean("integrations.discord.map-switch", false),
                List.copyOf(milestones),
                Math.min(120, Math.max(1, rate)),
                template(config, "kill-message", "%killer% killed %victim% (streak %streak%)"),
                template(config, "death-message", "%player% died"),
                template(config, "streak-message", "%player% reached a %streak% killstreak"),
                template(config, "map-message", "Map changed to %map%")
        );
    }

    public static FileConfiguration config(infinode.org.example.bFFA01.BFFA01 plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            return plugin.getConfig();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String mode(FileConfiguration config, String path, String fallback, String... allowed) {
        String raw = config == null ? null : config.getString(path, fallback);
        if (raw == null) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return value;
            }
        }
        return fallback;
    }

    private static String template(FileConfiguration config, String key, String fallback) {
        String value = config.getString("integrations.discord." + key, fallback);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() > 300 ? value.substring(0, 300) : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    public record Discord(boolean enabled, String url, String username, boolean kills, boolean deaths, boolean streaks,
                          boolean mapSwitch, List<Integer> milestones, int rateLimit, String killMessage,
                          String deathMessage, String streakMessage, String mapMessage) {

        public static Discord defaults() {
            return new Discord(false, "", "BuildFFA", true, false, true, false, DEFAULT_MILESTONES, 30,
                    "%killer% killed %victim% (streak %streak%)",
                    "%player% died",
                    "%player% reached a %streak% killstreak",
                    "Map changed to %map%");
        }

        public boolean milestone(int streak) {
            return milestones.contains(streak);
        }
    }
}
