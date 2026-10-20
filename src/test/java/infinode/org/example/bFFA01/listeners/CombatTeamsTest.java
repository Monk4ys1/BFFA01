package infinode.org.example.bFFA01.listeners;

import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatTeamsTest {

    @Test
    void enteringCombatLeavesTheSafeTeam() {
        Team combat = mock(Team.class);
        Team safe = mock(Team.class);
        when(safe.hasEntry("Alex")).thenReturn(true);
        when(combat.hasEntry("Alex")).thenReturn(false);

        CombatTeams.assign(combat, safe, "Alex", true);

        verify(safe).removeEntry("Alex");
        verify(combat).addEntry("Alex");
    }

    @Test
    void leavingCombatLeavesTheCombatTeam() {
        Team combat = mock(Team.class);
        Team safe = mock(Team.class);
        when(combat.hasEntry("Alex")).thenReturn(true);
        when(safe.hasEntry("Alex")).thenReturn(false);

        CombatTeams.assign(combat, safe, "Alex", false);

        verify(combat).removeEntry("Alex");
        verify(safe).addEntry("Alex");
        verify(combat, never()).addEntry("Alex");
    }
}
