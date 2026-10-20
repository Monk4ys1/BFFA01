package infinode.org.example.bFFA01.combat;

import java.util.UUID;

/**
 * One attacker's merged contribution to a life, inside the assist window.
 */
public record DamageShare(UUID attacker, String name, long hundredths, String weapon, boolean crit, boolean sweep) {

    public DamageShare {
        name = name == null ? "" : name;
        weapon = weapon == null ? "" : weapon;
    }

    public String label() {
        return name.isBlank() ? attacker.toString().substring(0, 8) : name;
    }
}
