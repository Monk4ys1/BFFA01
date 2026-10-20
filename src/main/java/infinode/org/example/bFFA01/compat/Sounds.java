package infinode.org.example.bFFA01.compat;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Sound enum constants are not referenced. A removed name is skipped.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, float volume, float pitch, String... names) {
        if (player == null || names == null) {
            return;
        }
        Location location = player.getLocation();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                Sound sound = Sound.valueOf(name);
                player.playSound(location, sound, volume, pitch);
                return;
            } catch (IllegalArgumentException ignored) {
                // Try the next historical name.
            }
        }
    }
}
