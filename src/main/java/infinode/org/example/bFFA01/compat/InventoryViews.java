package infinode.org.example.bFFA01.compat;

import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Method;

/**
 * InventoryView became an interface in 1.21. Calling its methods directly from bytecode
 * compiled against either side fails on the other, so the view is only touched reflectively.
 */
public final class InventoryViews {

    private InventoryViews() {
    }

    public static Inventory top(InventoryEvent event) {
        if (event == null) {
            return null;
        }
        Object view = event.getView();
        if (view == null) {
            return null;
        }
        try {
            Method method = view.getClass().getMethod("getTopInventory");
            return (Inventory) method.invoke(view);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not read the open inventory", ex);
        }
    }

    public static InventoryHolder holder(InventoryEvent event) {
        Inventory inventory = top(event);
        return inventory == null ? null : inventory.getHolder();
    }
}
