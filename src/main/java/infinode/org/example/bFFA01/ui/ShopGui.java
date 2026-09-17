package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.Upgrade;
import infinode.org.example.bFFA01.shop.ShopRegistry;
import infinode.org.example.bFFA01.shop.SpecialItem;
import infinode.org.example.bFFA01.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop.
 *
 * <p>Redesigned for 5.x: a framed six-row layout with a balance header, a
 * labelled upgrade row and two consumable rows. Every entry shows its price,
 * whether the player can afford it and whether an upgrade is already owned, and
 * a purchase refreshes the menu in place instead of closing it.
 */
public final class ShopGui extends Gui {

    private static final int BALANCE_SLOT = 4;
    private static final int UPGRADE_LABEL_SLOT = 0;
    private static final int ITEM_LABEL_SLOT = 18;
    private static final int STATS_SLOT = 47;
    private static final int CLOSE_SLOT = 49;
    private static final int KIT_SLOT = 51;

    public ShopGui(BFFA01 plugin, Player viewer) {
        super(plugin, viewer, 6, "&8» &b&lBuildFFA Shop");
    }

    @Override
    public void render() {
        getInventory().clear();
        ShopRegistry registry = plugin.shopRegistry();
        PlayerData data = plugin.dataManager().get(viewer);

        drawRow(0, FRAME_MATERIAL);
        drawRow(2, SEPARATOR_MATERIAL);
        drawRow(5, FRAME_MATERIAL);

        set(BALANCE_SLOT, balanceIcon(data));
        set(UPGRADE_LABEL_SLOT, label("&b&lPermanent Upgrades", "&7Bought once, kept forever."));
        set(ITEM_LABEL_SLOT, label("&d&lConsumables", "&7Single use, lost on death."));

        for (Upgrade upgrade : registry.enabledUpgrades()) {
            ShopRegistry.Entry entry = registry.entry(upgrade);
            boolean owned = data.hasUpgrade(upgrade.id());
            set(entry.slot(), upgradeIcon(entry, owned, data.coins()));
        }
        for (SpecialItem item : registry.enabledItems()) {
            ShopRegistry.Entry entry = registry.entry(item);
            set(entry.slot(), itemIcon(entry, data.coins()));
        }

        set(STATS_SLOT, ItemBuilder.of(Material.BOOK)
                .name("&e&lYour Stats")
                .lore("&7Kills: &a" + data.kills(),
                        "&7Deaths: &c" + data.deaths(),
                        "&7K/D: &6" + String.format("%.2f", data.kd()),
                        "",
                        "&8» &aClick to open the full menu")
                .build());
        set(KIT_SLOT, ItemBuilder.of(Material.CHEST)
                .name("&a&lKit Editor")
                .lore("&7Rearrange your hotbar layout.", "", "&8» &aClick to open")
                .build());
        set(CLOSE_SLOT, closeButton());

        fillEmpty(SEPARATOR_MATERIAL);
    }

    private org.bukkit.inventory.ItemStack balanceIcon(PlayerData data) {
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOf(viewer)
                .name("&6&l" + viewer.getName())
                .lore("&7Balance: &6" + data.coins() + " coins",
                        "&7Killstreak: &d" + plugin.killstreakManager().streak(viewer),
                        "",
                        "&8Earn coins by killing players.")
                .build();
    }

    private org.bukkit.inventory.ItemStack label(String name, String description) {
        return ItemBuilder.of(Material.NAME_TAG).name(name).lore(description).build();
    }

    private org.bukkit.inventory.ItemStack upgradeIcon(ShopRegistry.Entry entry, boolean owned, int coins) {
        List<String> lore = new ArrayList<>(entry.lore());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        if (owned) {
            lore.add("&7Price: &8" + entry.price() + " coins");
            lore.add("");
            lore.add("&a&l✔ Already owned");
        } else {
            lore.add("&7Price: &6" + entry.price() + " coins");
            lore.add("");
            lore.add(coins >= entry.price()
                    ? "&8» &aClick to buy"
                    : "&8» &cYou need " + (entry.price() - coins) + " more coins");
        }
        return ItemBuilder.of(entry.icon())
                .name(entry.name())
                .lore(lore)
                .glow(owned)
                .hideAttributes()
                .build();
    }

    private org.bukkit.inventory.ItemStack itemIcon(ShopRegistry.Entry entry, int coins) {
        List<String> lore = new ArrayList<>(entry.lore());
        if (!lore.isEmpty()) {
            lore.add("");
        }
        lore.add("&7Price: &6" + entry.price() + " coins");
        lore.add("");
        lore.add(coins >= entry.price()
                ? "&8» &aClick to buy"
                : "&8» &cYou need " + (entry.price() - coins) + " more coins");
        return ItemBuilder.of(entry.icon())
                .name(entry.name())
                .lore(lore)
                .hideAttributes()
                .build();
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
        if (slot == STATS_SLOT) {
            new StatsGui(plugin, viewer, plugin.dataManager().get(viewer)).open();
            return;
        }
        if (slot == KIT_SLOT) {
            new KitEditorGui(plugin, viewer).open();
            return;
        }

        ShopRegistry registry = plugin.shopRegistry();
        registry.upgradeAtSlot(slot).ifPresent(upgrade -> {
            ShopRegistry.Entry entry = registry.entry(upgrade);
            report(registry.buy(viewer, upgrade), entry);
        });
        registry.itemAtSlot(slot).ifPresent(item -> {
            ShopRegistry.Entry entry = registry.entry(item);
            report(registry.buy(viewer, item), entry);
        });
    }

    /** Turns a purchase result into feedback and refreshes the menu. */
    private void report(ShopRegistry.PurchaseResult result, ShopRegistry.Entry entry) {
        switch (result) {
            case SUCCESS -> {
                plugin.messages().send(viewer, "shop-purchased",
                        "item", infinode.org.example.bFFA01.util.Text.colorize(entry.name()),
                        "price", entry.price());
                Compat.playSound(viewer, "entity.player.levelup", 0.8F, 1.4F);
            }
            case ALREADY_OWNED -> {
                plugin.messages().send(viewer, "shop-already-owned");
                Compat.playSound(viewer, "entity.villager.no", 0.8F, 1.0F);
            }
            case NOT_ENOUGH_COINS -> {
                plugin.messages().send(viewer, "shop-not-enough-coins",
                        "missing", plugin.shopRegistry().missingCoins(viewer, entry.price()));
                Compat.playSound(viewer, "entity.villager.no", 0.8F, 1.0F);
            }
            case INVENTORY_FULL -> {
                plugin.messages().send(viewer, "shop-inventory-full");
                Compat.playSound(viewer, "entity.villager.no", 0.8F, 1.0F);
            }
            case DISABLED -> Compat.playSound(viewer, "entity.villager.no", 0.8F, 1.0F);
        }
        refresh();
    }
}
