package infinode.org.example.bFFA01.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyedCooldownTest {

    @Test
    void blocksOnlyTheSameKeyInsideTheWindow() {
        KeyedCooldown cooldown = new KeyedCooldown(3_000L);
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertEquals(0L, cooldown.remainingMillis(player, 1_000L));
        cooldown.markUsed(player, 1_000L);

        assertEquals(1L, cooldown.remainingMillis(player, 3_999L));
        assertEquals(0L, cooldown.remainingMillis(player, 4_000L));
        assertEquals(0L, cooldown.remainingMillis(other, 1_500L));
    }

    @Test
    void rejectsANonPositiveWindow() {
        assertThrows(IllegalArgumentException.class, () -> new KeyedCooldown(0L));
    }
}
