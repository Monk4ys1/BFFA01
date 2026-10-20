package infinode.org.example.bFFA01.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnchantmentsTest {

    @Test
    void legacyFieldNamesMapToStableIds() {
        assertEquals("sharpness", Enchantments.canonicalKey("DAMAGE_ALL"));
        assertEquals("power", Enchantments.canonicalKey("arrow_damage"));
        assertEquals("punch", Enchantments.canonicalKey("arrow_knockback"));
        assertEquals("protection", Enchantments.canonicalKey("protection_environmental"));
        assertEquals("feather_falling", Enchantments.canonicalKey("protection_fall"));
        assertEquals("feather_falling", Enchantments.canonicalKey("featherfalling"));
    }
}
