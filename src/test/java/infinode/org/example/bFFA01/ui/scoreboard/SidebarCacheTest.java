package infinode.org.example.bFFA01.ui.scoreboard;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarCacheTest {

    @Test
    void unchangedValuesSkipEveryLine() {
        Map<String, String> values = Map.of("kills", "4", "map", "Arena");
        assertTrue(SidebarCache.same(values, Map.of("kills", "4", "map", "Arena")));
        assertFalse(SidebarCache.same(values, Map.of("kills", "5", "map", "Arena")));
    }

    @Test
    void aLineRedrawsOnlyWhenItsTokenChanged() {
        Map<String, String> previous = Map.of("kills", "4", "map", "Arena", "time", "01:00");
        Map<String, String> next = Map.of("kills", "4", "map", "Arena", "time", "00:59");
        assertFalse(SidebarCache.lineAffected("Kills %kills%", previous, next));
        assertTrue(SidebarCache.lineAffected("Time %time%", previous, next));
        assertTrue(SidebarCache.lineAffected("Rank %bffa_rank%", previous, next));
    }
}
