package infinode.org.example.bFFA01.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Potion ids changed with the 1.20.5 rename (JUMP to jump_boost). Both keys are tried.
 */
public final class PotionEffects {

    private PotionEffects() {
    }

    public static PotionEffectType resolve(String... keys) {
        if (keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(normalized));
            if (type != null) {
                return type;
            }
            type = PotionEffectType.getByName(key.trim().toUpperCase(Locale.ROOT));
            if (type != null) {
                return type;
            }
        }
        return null;
    }

    public static PotionEffectType jumpBoost() {
        return resolve("jump_boost", "jump");
    }

    public static PotionEffectType speed() {
        return resolve("speed");
    }

    public static PotionEffectType invisibility() {
        return resolve("invisibility");
    }
}
