package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.event.inventory.ClickType;

/**
 * A menu chest cancels every click. Only plain and shift clicks run the slot action.
 * Number keys, offhand swap, and drops are cancelled and do not activate a button.
 */
public final class MenuClicks {

    private MenuClicks() {
    }

    public static boolean cancels(boolean menuOpen) {
        return menuOpen;
    }

    public static boolean activates(ClickType click) {
        return click == ClickType.LEFT
                || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT
                || click == ClickType.SHIFT_RIGHT;
    }
}
