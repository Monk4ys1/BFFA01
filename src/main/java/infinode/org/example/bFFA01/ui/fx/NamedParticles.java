package infinode.org.example.bFFA01.ui.fx;

import org.bukkit.Particle;

import java.util.List;
import java.util.Locale;

/**
 * Particle enum names move between versions. The first name that exists is used.
 */
public final class NamedParticles {

    private NamedParticles() {
    }

    public static Particle first(List<String> names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Try the next historical name.
            }
        }
        return null;
    }
}
