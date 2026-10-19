package infinode.org.example.bFFA01.hooks.hologram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramChoiceTest {

    @Test
    void autoPrefersDecentThenFancyThenArmorStands() {
        assertEquals("decentholograms", HologramChoice.choose("auto", true, true));
        assertEquals("fancyholograms", HologramChoice.choose(null, false, true));
        assertEquals("armorstand", HologramChoice.choose("auto", false, false));
    }

    @Test
    void explicitBackendFallsBackWhenThatPluginIsMissing() {
        assertEquals("armorstand", HologramChoice.choose("decent", false, true));
        assertEquals("fancyholograms", HologramChoice.choose("fancyholograms", true, true));
        assertEquals("armorstand", HologramChoice.choose("builtin", true, true));
    }
}
