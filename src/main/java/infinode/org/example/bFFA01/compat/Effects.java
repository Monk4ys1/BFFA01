package infinode.org.example.bFFA01.compat;

import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Version independent access to potion effect types.
 *
 * <p>Like enchantments, the API constants were renamed in 1.20.5
 * ({@code JUMP} became {@code JUMP_BOOST}, {@code SLOW} became
 * {@code SLOWNESS}, ...) while the vanilla registry keys stayed the same.
 */
public final class Effects {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        alias("speed", "SPEED");
        alias("slowness", "SLOWNESS", "SLOW");
        alias("haste", "HASTE", "FAST_DIGGING");
        alias("mining_fatigue", "MINING_FATIGUE", "SLOW_DIGGING");
        alias("strength", "STRENGTH", "INCREASE_DAMAGE");
        alias("instant_health", "INSTANT_HEALTH", "HEAL");
        alias("instant_damage", "INSTANT_DAMAGE", "HARM");
        alias("jump_boost", "JUMP_BOOST", "JUMP");
        alias("nausea", "NAUSEA", "CONFUSION");
        alias("regeneration", "REGENERATION");
        alias("resistance", "RESISTANCE", "DAMAGE_RESISTANCE");
        alias("fire_resistance", "FIRE_RESISTANCE");
        alias("water_breathing", "WATER_BREATHING");
        alias("invisibility", "INVISIBILITY");
        alias("blindness", "BLINDNESS");
        alias("night_vision", "NIGHT_VISION");
        alias("hunger", "HUNGER");
        alias("weakness", "WEAKNESS");
        alias("poison", "POISON");
        alias("wither", "WITHER");
        alias("health_boost", "HEALTH_BOOST");
        alias("absorption", "ABSORPTION");
        alias("saturation", "SATURATION");
        alias("glowing", "GLOWING");
        alias("levitation", "LEVITATION");
        alias("luck", "LUCK");
        alias("unluck", "UNLUCK", "BAD_LUCK");
        alias("slow_falling", "SLOW_FALLING");
        alias("conduit_power", "CONDUIT_POWER");
        alias("dolphins_grace", "DOLPHINS_GRACE");
        alias("bad_omen", "BAD_OMEN");
        alias("hero_of_the_village", "HERO_OF_THE_VILLAGE");
        alias("darkness", "DARKNESS");
    }

    private Effects() {
    }

    private static void alias(String key, String... names) {
        for (String name : names) {
            ALIASES.put(name.toLowerCase(Locale.ROOT), key);
        }
        ALIASES.put(key, key);
    }

    public static PotionEffectType speed() {
        return byName("speed");
    }

    public static PotionEffectType jumpBoost() {
        return byName("jump_boost");
    }

    public static PotionEffectType invisibility() {
        return byName("invisibility");
    }

    /**
     * Resolves a potion effect written in a config file. Accepts modern names,
     * legacy names and namespaced keys.
     *
     * @return the effect type, or {@code null} when the server does not know it
     */
    public static PotionEffectType byName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return null;
        }
        String normalised = rawName.trim().toLowerCase(Locale.ROOT);
        int colon = normalised.indexOf(':');
        if (colon >= 0) {
            normalised = normalised.substring(colon + 1);
        }
        String key = ALIASES.getOrDefault(normalised, normalised);
        return Compat.potionEffect(key, key.toUpperCase(Locale.ROOT), rawName.trim().toUpperCase(Locale.ROOT));
    }
}
