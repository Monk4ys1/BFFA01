package infinode.org.example.bFFA01.combat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recent hits used for assists, the death breakdown, and the running combo.
 */
public final class CombatTracker {

    private final DamageBook book = new DamageBook();
    private final Map<UUID, Long> voidSkip = new ConcurrentHashMap<>();

    public void record(UUID victim, UUID attacker, String name, long hundredths, String weapon, long atMillis, boolean crit, boolean sweep) {
        book.record(victim, attacker, name, hundredths, weapon, atMillis, crit, sweep);
    }

    public long combo(UUID victim, UUID attacker, long now, long windowMillis) {
        return book.combo(victim, attacker, now, windowMillis);
    }

    public List<DamageShare> settle(UUID victim, long now, long windowMillis) {
        voidSkip.remove(victim);
        return book.settle(victim, now, windowMillis);
    }

    public void skipNextVoid(UUID victim, long now) {
        if (victim != null) {
            voidSkip.put(victim, now + 1_500L);
        }
    }

    public boolean takeVoidSkip(UUID victim, long now) {
        Long until = victim == null ? null : voidSkip.remove(victim);
        return until != null && until >= now;
    }

    public void forget(UUID victim) {
        book.forget(victim);
        if (victim != null) {
            voidSkip.remove(victim);
        }
    }
}
