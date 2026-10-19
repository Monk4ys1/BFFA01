package infinode.org.example.bFFA01.compat;

import org.bukkit.Bukkit;
import org.bukkit.BanList;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Profile bans changed type between Paper and Spigot. The check stays reflective so the
 * jar does not link against Paper's profile class.
 * Returns true when the check itself fails, which keeps this plugin from replacing a ban
 * it could not inspect.
 */
public final class ProfileBans {

    private ProfileBans() {
    }

    public static boolean isBanned(Player player) {
        if (player == null) {
            return true;
        }
        try {
            Method getter = player.getClass().getMethod("getPlayerProfile");
            Object profile = getter.invoke(player);
            if (profile == null) {
                return false;
            }
            Object id = invokeIfPresent(profile, "getUniqueId");
            if (id == null) {
                id = invokeIfPresent(profile, "getId");
            }
            if (id == null) {
                return false;
            }
            Object bans = Bukkit.class.getMethod("getBanList", BanList.Type.class).invoke(null, BanList.Type.PROFILE);
            for (Method method : bans.getClass().getMethods()) {
                if (!"isBanned".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isInstance(profile)) {
                    continue;
                }
                return Boolean.TRUE.equals(method.invoke(bans, profile));
            }
            return false;
        } catch (Throwable ex) {
            return true;
        }
    }

    private static Object invokeIfPresent(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
