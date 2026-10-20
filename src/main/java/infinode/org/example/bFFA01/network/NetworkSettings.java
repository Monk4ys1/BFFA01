package infinode.org.example.bFFA01.network;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

/**
 * Cross-server options. {@code enabled: auto} turns the cluster on only when
 * storage is MySQL or MariaDB, so a single YAML server still needs nothing.
 */
public final class NetworkSettings {

    private final boolean enabled;
    private final int leaderboardSeconds;
    private final boolean messenger;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;
    private final String redisChannel;

    private NetworkSettings(boolean enabled, int leaderboardSeconds, boolean messenger, String redisHost, int redisPort,
                            String redisPassword, String redisChannel) {
        this.enabled = enabled;
        this.leaderboardSeconds = leaderboardSeconds;
        this.messenger = messenger;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisPassword = redisPassword;
        this.redisChannel = redisChannel;
    }

    public static NetworkSettings load(FileConfiguration config) {
        boolean enabled = enabled(config);
        int seconds = config == null ? 30 : config.getInt("network.leaderboard-seconds", 30);
        if (seconds < 5) {
            seconds = 5;
        } else if (seconds > 600) {
            seconds = 600;
        }
        boolean messenger = config == null || config.getBoolean("network.messenger", true);
        String host = config == null ? "" : config.getString("network.redis.host", "");
        host = host == null ? "" : host.trim();
        if (!host.isEmpty() && !host.matches("[A-Za-z0-9._-]{1,253}")) {
            host = "";
        }
        int port = config == null ? 6379 : config.getInt("network.redis.port", 6379);
        if (port < 1 || port > 65535) {
            port = 6379;
        }
        String password = config == null ? "" : config.getString("network.redis.password", "");
        password = password == null ? "" : password;
        String channel = config == null ? "bffa" : config.getString("network.redis.channel", "bffa");
        if (channel == null || channel.isBlank() || channel.length() > 64) {
            channel = "bffa";
        }
        return new NetworkSettings(enabled, seconds, messenger, host, port, password, channel.trim());
    }

    static boolean enabled(FileConfiguration config) {
        if (config == null) {
            return false;
        }
        Object raw = config.get("network.enabled");
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        String text = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "yes".equals(text) || "on".equals(text)) {
            return true;
        }
        if ("false".equals(text) || "no".equals(text) || "off".equals(text)) {
            return false;
        }
        return sharedStorage(config);
    }

    private static boolean sharedStorage(FileConfiguration config) {
        String type = config.getString("storage.type", "yaml");
        if (type == null) {
            return false;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return "mysql".equals(normalized) || "mariadb".equals(normalized);
    }

    public boolean enabled() {
        return enabled;
    }

    public int leaderboardSeconds() {
        return leaderboardSeconds;
    }

    public boolean messenger() {
        return messenger;
    }

    public boolean redis() {
        return enabled && !redisHost.isEmpty();
    }

    public String redisHost() {
        return redisHost;
    }

    public int redisPort() {
        return redisPort;
    }

    public String redisPassword() {
        return redisPassword;
    }

    public String redisChannel() {
        return redisChannel;
    }
}
