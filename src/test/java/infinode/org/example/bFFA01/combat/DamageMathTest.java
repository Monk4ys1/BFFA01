package infinode.org.example.bFFA01.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageMathTest {

    @Test
    void overkillStopsAtRemainingHealth() {
        assertEquals(400L, DamageMath.hundredths(12.0, 4.0, 0));
    }

    @Test
    void absorptionIsAddedOnTopOfHealthRemoved() {
        assertEquals(550L, DamageMath.hundredths(4.0, 4.0, 1.5));
    }

    @Test
    void badNumbersCountAsZero() {
        assertEquals(0L, DamageMath.hundredths(Double.NaN, 10, 0));
        assertEquals(0L, DamageMath.hundredths(-2, 10, -1));
        assertEquals(0L, DamageMath.hundredths(Double.POSITIVE_INFINITY, 10, 0));
    }

    @Test
    void formatDropsNeedlessZeros() {
        assertEquals("2", DamageMath.format(200));
        assertEquals("2.5", DamageMath.format(250));
        assertEquals("2.01", DamageMath.format(201));
    }
}
