package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.KitManager;
import infinode.org.example.bFFA01.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The kit editor.
 *
 * <p>4.x advertised a "Kit Editor UI" but {@code /kit} only printed a hint to
 * sort your hotbar and run {@code /savekit}. This is the actual menu: click two
 * slots to swap them, then save. The preview always shows the configured kit,
 * not whatever the player happens to be carrying, so consumables bought in the
 * shop cannot end up in the saved layout.
 */
public final class KitEditorGui extends Gui {

    private static final int FIRST_SLOT = 9;
    private static final int INFO_SLOT = 4;
    private static final int RESET_SLOT = 18;
    private static final int SAVE_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final ItemStack[] layout = new ItemStack[PlayerData.LAYOUT_SLOTS];
    private int selected = -1;

    public KitEditorGui(BFFA01 plugin, Player viewer) {
        super(plugin, viewer, 3, "&8» &a&lKit Editor");
        loadLayout();
    }

    /** Builds the preview from the configured kit plus the saved layout. */
    private void loadLayout() {
        java.util.Arrays.fill(layout, null);
        List<KitManager.KitEntry> pending = new ArrayList<>(plugin.kitManager().entries());
        PlayerData data = plugin.dataManager().get(viewer);

        if (data.hasLayout()) {
            for (int slot = 0; slot < PlayerData.LAYOUT_SLOTS; slot++) {
                String wanted = data.layoutSlot(slot);
                if (wanted == null) {
                    continue;
                }
                Material material = Material.matchMaterial(wanted);
                if (material == null) {
                    continue;
                }
                for (int i = 0; i < pending.size(); i++) {
                    if (pending.get(i).template().getType() == material) {
                        layout[slot] = pending.remove(i).template().clone();
                        break;
                    }
                }
            }
        }
        for (KitManager.KitEntry entry : pending) {
            int slot = entry.slot();
            if (slot < 0 || slot >= PlayerData.LAYOUT_SLOTS || layout[slot] != null) {
                slot = firstFree();
            }
            if (slot >= 0) {
                layout[slot] = entry.template().clone();
            }
        }
    }

    private int firstFree() {
        for (int slot = 0; slot < layout.length; slot++) {
            if (layout[slot] == null) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public void render() {
        getInventory().clear();
        drawRow(0, FRAME_MATERIAL);
        drawRow(2, FRAME_MATERIAL);

        set(INFO_SLOT, ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name("&b&lHow it works")
                .lore("&7Click an item, then click a second",
                        "&7slot to swap the two.",
                        "&7Press &aSave &7when you are happy.",
                        "",
                        "&8The layout is used every time you respawn.")
                .build());

        for (int index = 0; index < layout.length; index++) {
            set(FIRST_SLOT + index, slotIcon(index));
        }

        set(RESET_SLOT, ItemBuilder.of(Material.LAVA_BUCKET)
                .name("&c&lReset")
                .lore("&7Go back to the default kit order.")
                .build());
        set(SAVE_SLOT, ItemBuilder.of(Material.LIME_DYE)
                .name("&a&lSave")
                .lore("&7Store this layout on your profile.")
                .build());
        set(CLOSE_SLOT, closeButton());

        fillEmpty(SEPARATOR_MATERIAL);
    }

    /** Renders one hotbar slot, marking the selected one. */
    private ItemStack slotIcon(int index) {
        ItemStack source = layout[index];
        if (source == null) {
            return ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                    .name("&7Hotbar slot " + (index + 1))
                    .lore(index == selected ? "&e▶ Selected" : "&8Empty")
                    .build();
        }
        ItemStack icon = source.clone();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(infinode.org.example.bFFA01.util.Text.colorize("&8Hotbar slot " + (index + 1)));
            lore.add(infinode.org.example.bFFA01.util.Text.colorize(
                    index == selected ? "&e▶ Selected - click another slot" : "&8» &aClick to move"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(getInventory())) {
            return;
        }
        int slot = event.getRawSlot();

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }
        if (slot == SAVE_SLOT) {
            save();
            return;
        }
        if (slot == RESET_SLOT) {
            reset();
            return;
        }
        int index = slot - FIRST_SLOT;
        if (index < 0 || index >= layout.length) {
            return;
        }

        if (selected == -1) {
            selected = index;
            Compat.playSound(viewer, "ui.button.click", 0.5F, 1.6F);
        } else if (selected == index) {
            selected = -1;
            Compat.playSound(viewer, "ui.button.click", 0.5F, 0.8F);
        } else {
            ItemStack swap = layout[selected];
            layout[selected] = layout[index];
            layout[index] = swap;
            selected = -1;
            Compat.playSound(viewer, "block.note_block.hat", 0.7F, 1.4F);
        }
        refresh();
    }

    private void save() {
        PlayerData data = plugin.dataManager().get(viewer);
        data.clearLayout();
        for (int index = 0; index < layout.length; index++) {
            data.layoutSlot(index, layout[index] == null ? null : layout[index].getType().name());
        }
        plugin.dataManager().markDirty();
        plugin.messages().send(viewer, "kit-saved");
        Compat.playSound(viewer, "entity.player.levelup", 0.7F, 1.6F);
        viewer.closeInventory();
    }

    private void reset() {
        PlayerData data = plugin.dataManager().get(viewer);
        data.clearLayout();
        plugin.dataManager().markDirty();
        selected = -1;
        loadLayout();
        plugin.messages().send(viewer, "kit-reset");
        Compat.playSound(viewer, "entity.item.break", 0.7F, 1.0F);
        refresh();
    }
}
