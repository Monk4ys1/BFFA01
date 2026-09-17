package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.util.ItemBuilder;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Base class for every menu in the plugin.
 *
 * <p>Menus are identified by being their own {@link InventoryHolder}. 4.x
 * compared {@code event.getView().getTitle()} instead, which is both fragile
 * (a renamed title silently disables the shop) and outright broken on 1.21,
 * where {@code InventoryView} changed from a class into an interface and the
 * compiled call site throws {@code IncompatibleClassChangeError}.
 */
public abstract class Gui implements InventoryHolder {

    /** Filler used for the frame around a menu. */
    protected static final Material FRAME_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    /** Filler used inside a menu to separate sections. */
    protected static final Material SEPARATOR_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

    protected final BFFA01 plugin;
    protected final Player viewer;
    private final Inventory inventory;

    // createInventory only stores the holder reference, it never calls back
    // into this object, so handing out 'this' here is safe.
    @SuppressWarnings("this-escape")
    protected Gui(BFFA01 plugin, Player viewer, int rows, String title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.colorize(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Fills the menu with its current content. Called on open and refresh. */
    public abstract void render();

    /** Handles a click. The event is already cancelled when this runs. */
    public abstract void onClick(InventoryClickEvent event);

    /** Optional hook for cleanup. */
    public void onClose(InventoryCloseEvent event) {
        // Nothing by default.
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    /** Re-renders in place, so buying something does not close the menu. */
    public void refresh() {
        render();
        viewer.updateInventory();
    }

    // ------------------------------------------------------------------
    // Rendering helpers
    // ------------------------------------------------------------------

    protected void set(int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }

    /** A blank, unnamed pane used as decoration. */
    protected ItemStack filler(Material material) {
        return ItemBuilder.of(material).name(" ").build();
    }

    /** Draws a frame around the outside of the menu. */
    protected void drawFrame(Material material) {
        int size = inventory.getSize();
        int rows = size / 9;
        ItemStack pane = filler(material);
        for (int column = 0; column < 9; column++) {
            set(column, pane);
            set(size - 9 + column, pane);
        }
        for (int row = 1; row < rows - 1; row++) {
            set(row * 9, pane);
            set(row * 9 + 8, pane);
        }
    }

    /** Fills a whole row with a filler pane. */
    protected void drawRow(int row, Material material) {
        ItemStack pane = filler(material);
        for (int column = 0; column < 9; column++) {
            set(row * 9 + column, pane);
        }
    }

    /** Replaces every empty slot with a filler pane. */
    protected void fillEmpty(Material material) {
        ItemStack pane = filler(material);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, pane);
            }
        }
    }

    protected ItemStack closeButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("&c&lClose")
                .lore("&7Close this menu.")
                .build();
    }
}
