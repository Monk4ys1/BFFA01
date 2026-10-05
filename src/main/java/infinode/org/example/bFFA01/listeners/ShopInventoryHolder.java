package infinode.org.example.bFFA01.listeners;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifies the plugin shop inventory. Title strings are not trusted because
 * any view with the same title would skip inventory restrictions.
 */
public final class ShopInventoryHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
