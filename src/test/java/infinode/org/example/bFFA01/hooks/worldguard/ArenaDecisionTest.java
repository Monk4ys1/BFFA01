package infinode.org.example.bFFA01.hooks.worldguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaDecisionTest {

    @Test
    void inactiveIntegrationAndMissingFlagStayOpen() {
        assertTrue(ArenaDecision.allows(false, true, true, false, Boolean.FALSE));
        assertTrue(ArenaDecision.allows(true, false, true, false, null));
    }

    @Test
    void denyFlagBlocksEvenInsideTheArena() {
        assertFalse(ArenaDecision.allows(true, false, false, true, false));
    }

    @Test
    void regionRestrictionOnlyAppliesWhenConfigured() {
        assertTrue(ArenaDecision.allows(true, true, false, false, true));
        assertFalse(ArenaDecision.allows(true, true, true, false, true));
        assertTrue(ArenaDecision.allows(true, true, true, true, true));
        assertTrue(ArenaDecision.allows(true, false, true, false, true));
    }
}
