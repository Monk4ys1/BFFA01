package infinode.org.example.bFFA01.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes inventory events to the {@link Gui} that owns the inventory.
 *
 * <p>Clicks are cancelled before the menu sees them, so a menu can never leak
 * items into the world by forgetting a {@code setCancelled} call.
 */
public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Gui gui = guiOf(event.getInventory().getHolder());
        if (gui == null) {
            return;
        }
        event.setCancelled(true);
        gui.onClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (guiOf(event.getInventory().getHolder()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Gui gui = guiOf(event.getInventory().getHolder());
        if (gui != null) {
            gui.onClose(event);
        }
    }

    private Gui guiOf(InventoryHolder holder) {
        return holder instanceof Gui gui ? gui : null;
    }
}
