package infinode.org.example.bFFA01.compat;

import org.bukkit.enchantments.Enchantment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Version independent access to the enchantments this plugin uses.
 *
 * <p>Bukkit renamed every enchantment constant in 1.20.5 (for example
 * {@code DAMAGE_ALL} became {@code SHARPNESS}). The vanilla registry keys never
 * changed, so lookups go through the key and only fall back to constant names.
 */
public final class Enchants {

    /** Maps both the modern and the legacy API name onto the vanilla key. */
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("sharpness", "SHARPNESS", "DAMAGE_ALL");
        alias("smite", "SMITE", "DAMAGE_UNDEAD");
        alias("bane_of_arthropods", "BANE_OF_ARTHROPODS", "DAMAGE_ARTHROPODS");
        alias("knockback", "KNOCKBACK");
        alias("fire_aspect", "FIRE_ASPECT");
        alias("looting", "LOOTING", "LOOT_BONUS_MOBS");
        alias("sweeping_edge", "SWEEPING_EDGE", "SWEEPING");
        alias("protection", "PROTECTION", "PROTECTION_ENVIRONMENTAL");
        alias("fire_protection", "FIRE_PROTECTION", "PROTECTION_FIRE");
        alias("feather_falling", "FEATHER_FALLING", "PROTECTION_FALL");
        alias("blast_protection", "BLAST_PROTECTION", "PROTECTION_EXPLOSIONS");
        alias("projectile_protection", "PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE");
        alias("respiration", "RESPIRATION", "OXYGEN");
        alias("aqua_affinity", "AQUA_AFFINITY", "WATER_WORKER");
        alias("thorns", "THORNS");
        alias("depth_strider", "DEPTH_STRIDER");
        alias("frost_walker", "FROST_WALKER");
        alias("binding_curse", "BINDING_CURSE");
        alias("power", "POWER", "ARROW_DAMAGE");
        alias("punch", "PUNCH", "ARROW_KNOCKBACK");
        alias("flame", "FLAME", "ARROW_FIRE");
        alias("infinity", "INFINITY", "ARROW_INFINITE");
        alias("unbreaking", "UNBREAKING", "DURABILITY");
        alias("mending", "MENDING");
        alias("efficiency", "EFFICIENCY", "DIG_SPEED");
        alias("fortune", "FORTUNE", "LOOT_BONUS_BLOCKS");
        alias("silk_touch", "SILK_TOUCH");
        alias("luck_of_the_sea", "LUCK_OF_THE_SEA", "LUCK");
        alias("lure", "LURE");
        alias("loyalty", "LOYALTY");
        alias("impaling", "IMPALING");
        alias("riptide", "RIPTIDE");
        alias("channeling", "CHANNELING");
        alias("multishot", "MULTISHOT");
        alias("quick_charge", "QUICK_CHARGE");
        alias("piercing", "PIERCING");
        alias("soul_speed", "SOUL_SPEED");
        alias("swift_sneak", "SWIFT_SNEAK");
        alias("vanishing_curse", "VANISHING_CURSE");
    }

    private Enchants() {
    }

    private static void alias(String key, String... names) {
        for (String name : names) {
            ALIASES.put(name.toLowerCase(Locale.ROOT), key);
        }
        ALIASES.put(key, key);
    }

    public static Enchantment sharpness() {
        return byName("sharpness");
    }

    public static Enchantment power() {
        return byName("power");
    }

    public static Enchantment punch() {
        return byName("punch");
    }

    public static Enchantment knockback() {
        return byName("knockback");
    }

    public static Enchantment protection() {
        return byName("protection");
    }

    public static Enchantment featherFalling() {
        return byName("feather_falling");
    }

    /**
     * Resolves an enchantment written in a config file. Accepts modern names
     * ({@code SHARPNESS}), legacy names ({@code DAMAGE_ALL}) and namespaced
     * keys ({@code minecraft:sharpness}).
     *
     * @return the enchantment, or {@code null} when the server does not know it
     */
    public static Enchantment byName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return null;
        }
        String normalised = rawName.trim().toLowerCase(Locale.ROOT);
        int colon = normalised.indexOf(':');
        if (colon >= 0) {
            normalised = normalised.substring(colon + 1);
        }
        String key = ALIASES.getOrDefault(normalised, normalised);
        return Compat.enchantment(key, key.toUpperCase(Locale.ROOT), rawName.trim().toUpperCase(Locale.ROOT));
    }
}
