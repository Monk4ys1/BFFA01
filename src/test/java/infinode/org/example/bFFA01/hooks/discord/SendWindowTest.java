package infinode.org.example.bFFA01.hooks.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SendWindowTest {

    @Test
    void limitBlocksUntilTheWindowSlides() {
        SendWindow window = new SendWindow(2, 1_000L);
        assertTrue(window.tryAcquire(1_000L));
        assertTrue(window.tryAcquire(1_100L));
        assertFalse(window.tryAcquire(1_200L));
        assertEquals(2, window.size());
        assertTrue(window.tryAcquire(2_099L));
        assertEquals(2, window.size());
    }
}
