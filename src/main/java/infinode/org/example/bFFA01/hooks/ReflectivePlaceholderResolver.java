package infinode.org.example.bFFA01.hooks;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Calls PlaceholderAPI when it is installed, without a compile dependency.
 */
public final class ReflectivePlaceholderResolver implements PlaceholderResolver {

    @Override
    public String apply(Player player, String text) {
        if (player == null || text == null || text.indexOf('%') < 0) {
            return text == null ? "" : text;
        }
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method set = api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            Object result = set.invoke(null, player, text);
            return result instanceof String rendered ? rendered : text;
        } catch (Throwable ex) {
            return text;
        }
    }
}
