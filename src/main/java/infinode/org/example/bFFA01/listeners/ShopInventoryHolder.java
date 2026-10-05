package infinode.org.example.bFFA01.listeners;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identifies the plugin shop inventory. Title strings are not trusted because
 * any view with the same title would skip inventory restrictions.
 */
public final class ShopInventoryHolder implements InventoryHolder {

    static final int SIZE = 54;
    static final String TITLE = "Shop & Upgrades";

    private final Inventory inventory;

    public ShopInventoryHolder() {
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
