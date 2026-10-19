package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Identifies a plugin menu. The action map is the only thing a click is allowed to run.
 */
public final class MenuHolder implements InventoryHolder {

    private final String id;
    private final int page;
    private final String arg;
    private final Inventory inventory;
    private final Map<Integer, String> actions = new HashMap<>();
    private List<String> kit = List.of();
    private int selected = -1;

    public MenuHolder(String id, int page, String arg, int size, String title) {
        this.id = id;
        this.page = page;
        this.arg = arg == null ? "" : arg;
        this.inventory = Bukkit.createInventory(this, size, title == null || title.isEmpty() ? "BuildFFA" : title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public String id() {
        return id;
    }

    public int page() {
        return page;
    }

    public String arg() {
        return arg;
    }

    public void action(int slot, String action) {
        if (action == null || action.isBlank() || "none".equals(action)) {
            actions.remove(slot);
            return;
        }
        actions.put(slot, action);
    }

    public String action(int slot) {
        return actions.get(slot);
    }

    public List<String> kit() {
        return kit;
    }

    public void kit(List<String> kit) {
        this.kit = kit == null ? List.of() : List.copyOf(kit);
    }

    public int selected() {
        return selected;
    }

    public void selected(int selected) {
        this.selected = selected;
    }
}
