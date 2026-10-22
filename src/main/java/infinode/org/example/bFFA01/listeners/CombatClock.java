package infinode.org.example.bFFA01.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bumps a generation only when someone's combat flag changes.
 * Scoreboards rescan nametags on a new generation and skip the scan while it stays put.
 */
public final class CombatClock {

    private final Map<UUID, Boolean> flags = new HashMap<>();
    private int generation;

    public synchronized Mark note(UUID id, boolean inCombat) {
        if (id == null) {
            return new Mark(false, generation);
        }
        Boolean previous = flags.put(id, inCombat);
        boolean changed = previous == null || previous != inCombat;
        if (changed) {
            generation++;
        }
        return new Mark(changed, generation);
    }

    public synchronized void forget(UUID id) {
        if (id != null) {
            flags.remove(id);
        }
    }

    public static boolean rescan(int seen, int generation) {
        return seen != generation;
    }

    public record Mark(boolean changed, int generation) {
    }
}
