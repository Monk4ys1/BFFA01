package infinode.org.example.bFFA01.game;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks who is in build mode. Build mode bypasses arena protection so admins
 * can edit a map while the server keeps running, which is what {@code /build}
 * promised in the documentation but never implemented.
 */
public final class BuildModeService {

    private final Set<UUID> enabled = new HashSet<>();

    /**
     * Flips build mode for a player.
     *
     * @return the new state
     */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabled.remove(uuid)) {
            return false;
        }
        enabled.add(uuid);
        return true;
    }

    public boolean isEnabled(Player player) {
        return player != null && enabled.contains(player.getUniqueId());
    }

    public void disable(UUID uuid) {
        enabled.remove(uuid);
    }

    public void clear() {
        enabled.clear();
    }
}
