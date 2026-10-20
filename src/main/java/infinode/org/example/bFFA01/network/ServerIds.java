package infinode.org.example.bFFA01.network;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Lease holder id for this process. The value is adopted at startup from
 * {@link InstanceIds} after a registry claim. A data-folder file is never used,
 * because a copied volume would make two instances look like one.
 */
public final class ServerIds {

    private ServerIds() {
    }

    public static String current(JavaPlugin plugin) {
        return InstanceIds.current();
    }

    static String clip(String id) {
        String cleaned = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return "local";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }
}
