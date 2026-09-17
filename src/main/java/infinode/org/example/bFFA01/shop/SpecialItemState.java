package infinode.org.example.bFFA01.shop;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Short lived state for special items whose effect spans two events, currently
 * only the vampire fang (armed on right-click, consumed on the next hit).
 */
public final class SpecialItemState {

    private final Set<UUID> armedVampireFang = new HashSet<>();

    public void armVampireFang(UUID uuid) {
        armedVampireFang.add(uuid);
    }

    /**
     * Consumes an armed fang.
     *
     * @return {@code true} when the player had one armed
     */
    public boolean consumeVampireFang(UUID uuid) {
        return armedVampireFang.remove(uuid);
    }

    public void clear(UUID uuid) {
        armedVampireFang.remove(uuid);
    }

    public void clearAll() {
        armedVampireFang.clear();
    }
}
