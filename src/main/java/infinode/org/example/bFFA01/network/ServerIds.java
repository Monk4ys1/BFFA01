package infinode.org.example.bFFA01.network;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.UUID;

/**
 * Stable id for MySQL leases. A blank config value is remembered in server-id.txt
 * so a restart does not look like a different server.
 */
public final class ServerIds {

    private ServerIds() {
    }

    public static String current(JavaPlugin plugin) {
        String configured = configured(plugin);
        if (!configured.isBlank()) {
            return clip(configured);
        }
        File file = new File(plugin.getDataFolder(), "server-id.txt");
        try {
            if (file.isFile()) {
                String stored = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
                if (!stored.isBlank()) {
                    return clip(stored);
                }
            }
            String generated = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Files.writeString(file.toPath(), generated, StandardCharsets.UTF_8);
            if (networkEnabled(plugin)) {
                plugin.getLogger().warning("network.server-id is blank. Using generated id " + generated + " from server-id.txt.");
            }
            return generated;
        } catch (IOException ex) {
            return "local";
        }
    }

    static String clip(String id) {
        String cleaned = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return "local";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static String configured(JavaPlugin plugin) {
        try {
            String value = plugin.getConfig().getString("network.server-id", "");
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean networkEnabled(JavaPlugin plugin) {
        try {
            return plugin.getConfig().getBoolean("network.enabled", false);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
