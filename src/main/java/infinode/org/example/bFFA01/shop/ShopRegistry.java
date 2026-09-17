package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.Upgrade;
import infinode.org.example.bFFA01.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prices, slots and names for everything the shop sells, plus the purchase
 * rules themselves. Keeping the rules here means the GUI only has to render and
 * report, and the same purchase path is used no matter where it is triggered.
 */
public final class ShopRegistry {

    /** Size of the shop inventory in slots. */
    public static final int SHOP_SIZE = 54;

    private final BFFA01 plugin;
    private final NamespacedKey itemKey;
    private final Map<Upgrade, Entry> upgrades = new EnumMap<>(Upgrade.class);
    private final Map<SpecialItem, Entry> items = new EnumMap<>(SpecialItem.class);

    public ShopRegistry(BFFA01 plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "special-item");
    }

    /** One configurable shop entry. */
    public record Entry(String id, boolean enabled, int slot, int price, String name,
                        List<String> lore, Material icon) {
    }

    /** Outcome of a purchase attempt, rendered by the caller. */
    public enum PurchaseResult {
        SUCCESS,
        ALREADY_OWNED,
        NOT_ENOUGH_COINS,
        INVENTORY_FULL,
        DISABLED
    }

    /** Key used to tag shop items so listeners can identify them. */
    public NamespacedKey itemKey() {
        return itemKey;
    }

    public void reload() {
        upgrades.clear();
        items.clear();

        ConfigurationSection shop = plugin.getConfig().getConfigurationSection("shop");
        ConfigurationSection upgradeSection = shop == null ? null : shop.getConfigurationSection("upgrades");
        ConfigurationSection itemSection = shop == null ? null : shop.getConfigurationSection("items");

        for (Upgrade upgrade : Upgrade.values()) {
            upgrades.put(upgrade, read(
                    upgradeSection == null ? null : upgradeSection.getConfigurationSection(upgrade.id()),
                    upgrade.id(), upgrade.defaultSlot(), upgrade.defaultPrice(),
                    upgrade.displayName(), List.of(), upgrade.icon()));
        }
        for (SpecialItem item : SpecialItem.values()) {
            items.put(item, read(
                    itemSection == null ? null : itemSection.getConfigurationSection(item.id()),
                    item.id(), item.defaultSlot(), item.defaultPrice(),
                    item.displayName(), item.defaultLore(), item.material()));
        }
    }

    private Entry read(ConfigurationSection section, String id, int defaultSlot, int defaultPrice,
                       String defaultName, List<String> defaultLore, Material defaultIcon) {
        if (section == null) {
            return new Entry(id, true, defaultSlot, defaultPrice, defaultName, defaultLore, defaultIcon);
        }
        Material icon = defaultIcon;
        String iconName = section.getString("material");
        if (iconName != null) {
            Material parsed = Material.matchMaterial(iconName);
            if (parsed != null) {
                icon = parsed;
            } else {
                plugin.getLogger().warning("Unknown shop material '" + iconName + "' for '" + id + "'.");
            }
        }
        List<String> lore = section.getStringList("lore");
        return new Entry(
                id,
                section.getBoolean("enabled", true),
                section.getInt("slot", defaultSlot),
                Math.max(0, section.getInt("price", defaultPrice)),
                section.getString("name", defaultName),
                lore.isEmpty() ? defaultLore : lore,
                icon);
    }

    public Entry entry(Upgrade upgrade) {
        return upgrades.get(upgrade);
    }

    public Entry entry(SpecialItem item) {
        return items.get(item);
    }

    public List<Upgrade> enabledUpgrades() {
        List<Upgrade> result = new ArrayList<>();
        for (Map.Entry<Upgrade, Entry> entry : upgrades.entrySet()) {
            if (entry.getValue().enabled()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public List<SpecialItem> enabledItems() {
        List<SpecialItem> result = new ArrayList<>();
        for (Map.Entry<SpecialItem, Entry> entry : items.entrySet()) {
            if (entry.getValue().enabled()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public Optional<Upgrade> upgradeAtSlot(int slot) {
        return upgrades.entrySet().stream()
                .filter(entry -> entry.getValue().enabled() && entry.getValue().slot() == slot)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Optional<SpecialItem> itemAtSlot(int slot) {
        return items.entrySet().stream()
                .filter(entry -> entry.getValue().enabled() && entry.getValue().slot() == slot)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Purchasing
    // ------------------------------------------------------------------

    /** Buys a permanent upgrade and re-applies the kit when it succeeds. */
    public PurchaseResult buy(Player player, Upgrade upgrade) {
        Entry entry = upgrades.get(upgrade);
        if (entry == null || !entry.enabled()) {
            return PurchaseResult.DISABLED;
        }
        PlayerData data = plugin.dataManager().get(player);
        if (data.hasUpgrade(upgrade.id())) {
            return PurchaseResult.ALREADY_OWNED;
        }
        if (!data.spendCoins(entry.price())) {
            return PurchaseResult.NOT_ENOUGH_COINS;
        }
        data.addUpgrade(upgrade.id());
        plugin.dataManager().markDirty();
        // Enchant what the player is holding instead of re-issuing the kit,
        // which would throw away the consumables they already bought.
        plugin.kitManager().applyUpgrade(player, upgrade);
        return PurchaseResult.SUCCESS;
    }

    /** Buys one consumable and puts it in the player's inventory. */
    public PurchaseResult buy(Player player, SpecialItem item) {
        Entry entry = items.get(item);
        if (entry == null || !entry.enabled()) {
            return PurchaseResult.DISABLED;
        }
        if (player.getInventory().firstEmpty() == -1) {
            return PurchaseResult.INVENTORY_FULL;
        }
        PlayerData data = plugin.dataManager().get(player);
        if (!data.spendCoins(entry.price())) {
            return PurchaseResult.NOT_ENOUGH_COINS;
        }
        plugin.dataManager().markDirty();
        player.getInventory().addItem(create(item));
        return PurchaseResult.SUCCESS;
    }

    /** Builds the tagged item stack a player receives when buying. */
    public ItemStack create(SpecialItem item) {
        Entry entry = items.get(item);
        String name = entry == null ? item.displayName() : entry.name();
        Material icon = entry == null ? item.material() : entry.icon();
        return ItemBuilder.of(icon)
                .name(name)
                .lore(entry == null ? item.defaultLore() : entry.lore())
                .tag(itemKey, item.id())
                .hideAttributes()
                .build();
    }

    /** Reads the shop id back off an item, or {@code null} for a normal item. */
    public SpecialItem identify(ItemStack stack) {
        return SpecialItem.byId(ItemBuilder.readTag(stack, itemKey));
    }

    /** Coins the player is still missing for an entry, never negative. */
    public int missingCoins(Player player, int price) {
        return Math.max(0, price - plugin.dataManager().get(player).coins());
    }
}
