package infinode.org.example.bFFA01.ui.menu;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.InventoryViews;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Menu clicks never move items. Left and right click both activate a slot, which is what Bedrock sends.
 */
public final class MenuListener implements Listener {

    private final BFFA01 plugin;

    public MenuListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = InventoryViews.top(event);
        if (top == null || !(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }
        if (plugin.presentation() == null) {
            return;
        }
        plugin.presentation().menus().click(player, holder, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = InventoryViews.top(event);
        if (top != null && top.getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
