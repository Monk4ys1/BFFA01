package infinode.org.example.bFFA01.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.Locale;
import java.util.Map;

/**
 * Enchantments are resolved by their stable minecraft id.
 * Field names such as DAMAGE_ALL and SHARPNESS changed in 1.20.5 and are not referenced.
 */
public final class Enchantments {

    private static final Map<String, String> KEYS = Map.ofEntries(
            Map.entry("sharpness", "sharpness"),
            Map.entry("damage_all", "sharpness"),
            Map.entry("power", "power"),
            Map.entry("arrow_damage", "power"),
            Map.entry("punch", "punch"),
            Map.entry("arrow_knockback", "punch"),
            Map.entry("knockback", "knockback"),
            Map.entry("protection", "protection"),
            Map.entry("protection_environmental", "protection"),
            Map.entry("feather_falling", "feather_falling"),
            Map.entry("featherfalling", "feather_falling"),
            Map.entry("protection_fall", "feather_falling")
    );

    private Enchantments() {
    }

    public static String canonicalKey(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return KEYS.getOrDefault(normalized, normalized);
    }

    public static Enchantment resolve(String name) {
        String key = canonicalKey(name);
        if (key.isEmpty()) {
            return null;
        }
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(key));
        if (enchantment != null) {
            return enchantment;
        }
        enchantment = Enchantment.getByName(name.trim().toUpperCase(Locale.ROOT));
        if (enchantment != null) {
            return enchantment;
        }
        return Enchantment.getByName(key.toUpperCase(Locale.ROOT));
    }
}
