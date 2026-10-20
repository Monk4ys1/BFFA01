package infinode.org.example.bFFA01.listeners;

import org.bukkit.scoreboard.Team;

/**
 * A player is on one combat team at a time. Leaving them on both makes the nametag color undefined.
 */
public final class CombatTeams {

    private CombatTeams() {
    }

    public static void assign(Team combat, Team safe, String name, boolean inCombat) {
        if (name == null || name.isEmpty()) {
            return;
        }
        if (inCombat) {
            if (safe != null && safe.hasEntry(name)) {
                safe.removeEntry(name);
            }
            if (combat != null && !combat.hasEntry(name)) {
                combat.addEntry(name);
            }
            return;
        }
        if (combat != null && combat.hasEntry(name)) {
            combat.removeEntry(name);
        }
        if (safe != null && !safe.hasEntry(name)) {
            safe.addEntry(name);
        }
    }
}
