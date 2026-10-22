package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuClicksTest {

    @Test
    void menuClicksAreCancelledAndOnlyPlainClicksActivate() {
        assertTrue(MenuClicks.cancels(true));
        assertFalse(MenuClicks.cancels(false));
        assertTrue(MenuClicks.activates(ClickType.LEFT));
        assertTrue(MenuClicks.activates(ClickType.RIGHT));
        assertTrue(MenuClicks.activates(ClickType.SHIFT_LEFT));
        assertTrue(MenuClicks.activates(ClickType.SHIFT_RIGHT));
        assertFalse(MenuClicks.activates(ClickType.NUMBER_KEY));
        assertFalse(MenuClicks.activates(ClickType.SWAP_OFFHAND));
        assertFalse(MenuClicks.activates(ClickType.DROP));
        assertFalse(MenuClicks.activates(ClickType.CONTROL_DROP));
        assertFalse(MenuClicks.activates(ClickType.DOUBLE_CLICK));
        assertFalse(MenuClicks.activates(ClickType.WINDOW_BORDER_LEFT));
    }
}
