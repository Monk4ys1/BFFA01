package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.Map;

/**
 * In-memory config overrides from the process environment. The file on disk
 * stays the image default so every replica can share it.
 */
public final class EnvOverlay {

    private EnvOverlay() {
    }

    public static void apply(FileConfiguration config) {
        apply(config, System.getenv());
    }

    public static void apply(FileConfiguration config, Map<String, String> env) {
        if (config == null || env == null || env.isEmpty()) {
            return;
        }
        set(config, env, "BFFA_LANGUAGE", "language");
        set(config, env, "BFFA_STORAGE_TYPE", "storage.type");
        set(config, env, "BFFA_SQLITE_FILE", "storage.sqlite.file");
        set(config, env, "BFFA_MYSQL_HOST", "storage.mysql.host");
        setInt(config, env, "BFFA_MYSQL_PORT", "storage.mysql.port");
        set(config, env, "BFFA_MYSQL_DATABASE", "storage.mysql.database");
        set(config, env, "BFFA_MYSQL_USERNAME", "storage.mysql.username");
        set(config, env, "BFFA_MYSQL_PASSWORD", "storage.mysql.password");
        set(config, env, "BFFA_MYSQL_PARAMETERS", "storage.mysql.parameters");
        set(config, env, "BFFA_NETWORK", "network.enabled");
        set(config, env, "BFFA_REDIS_HOST", "network.redis.host");
        setInt(config, env, "BFFA_REDIS_PORT", "network.redis.port");
        set(config, env, "BFFA_REDIS_PASSWORD", "network.redis.password");
        set(config, env, "BFFA_REDIS_CHANNEL", "network.redis.channel");
        set(config, env, "BFFA_NETWORK_SECRET", "network.secret");
    }

    private static void set(FileConfiguration config, Map<String, String> env, String key, String path) {
        if (!env.containsKey(key)) {
            return;
        }
        String value = env.get(key);
        if (value == null) {
            return;
        }
        if ("BFFA_NETWORK".equals(key)) {
            String mode = value.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(mode) || "false".equals(mode)) {
                config.set(path, Boolean.parseBoolean(mode));
            } else {
                config.set(path, "auto");
            }
            return;
        }
        config.set(path, value.trim());
    }

    private static void setInt(FileConfiguration config, Map<String, String> env, String key, String path) {
        if (!env.containsKey(key)) {
            return;
        }
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed > 0 && parsed <= 65535) {
                config.set(path, parsed);
            }
        } catch (NumberFormatException ignored) {
            // Keep the file value when the variable is not a port.
        }
    }
}
