package infinode.org.example.bFFA01.api;

import java.util.UUID;

/**
 * Damage snapshot safe to read off the player's region thread.
 * {@link #damage()} is health points actually applied, not overkill.
 */
public record DamageCredit(UUID playerId, String name, double damage, String weapon) {

    public DamageCredit {
        name = name == null ? "" : name;
        weapon = weapon == null ? "" : weapon;
        if (Double.isNaN(damage) || damage < 0) {
            damage = 0;
        }
    }
}
