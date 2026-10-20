package infinode.org.example.bFFA01.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageBookTest {

    @Test
    void windowMergesOneAttackerAndDropsOldHits() {
        DamageBook book = new DamageBook();
        UUID victim = UUID.randomUUID();
        UUID sword = UUID.randomUUID();
        UUID bow = UUID.randomUUID();
        long now = 10_000L;
        book.record(victim, sword, "Ada", 200, "IRON_SWORD", now - 20_000L, false, false);
        book.record(victim, sword, "Ada", 150, "IRON_SWORD", now - 1_000L, true, false);
        book.record(victim, sword, "Ada", 50, "STICK", now - 100L, false, true);
        book.record(victim, bow, "Bea", 300, "BOW", now - 500L, false, false);

        assertEquals(200L, book.combo(victim, sword, now, 12_000L));

        List<DamageShare> shares = book.settle(victim, now, 12_000L);
        assertEquals(2, shares.size());
        assertEquals(bow, shares.get(0).attacker());
        assertEquals(300L, shares.get(0).hundredths());
        assertEquals(sword, shares.get(1).attacker());
        assertEquals(200L, shares.get(1).hundredths());
        assertEquals("IRON_SWORD", shares.get(1).weapon());
        assertTrue(shares.get(1).crit());
        assertTrue(shares.get(1).sweep());
        assertTrue(book.settle(victim, now, 12_000L).isEmpty());
    }
}
