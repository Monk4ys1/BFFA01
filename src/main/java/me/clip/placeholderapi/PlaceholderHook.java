package me.clip.placeholderapi;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Compile-time stub. The shaded jar excludes {@code me.clip} so the real
 * PlaceholderAPI class is used when the expansion is registered.
 */
public abstract class PlaceholderHook {

    public String onRequest(OfflinePlayer player, String params) {
        return null;
    }

    public String onPlaceholderRequest(Player player, String params) {
        return null;
    }
}
