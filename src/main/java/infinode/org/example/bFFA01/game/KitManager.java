package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Enchants;
import infinode.org.example.bFFA01.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and hands out the BuildFFA kit.
 *
 * <p>The kit is parsed once per reload into templates; handing it out only
 * clones those templates and applies the player's purchased upgrades and saved
 * hotbar layout.
 */
public final class KitManager {

    private final BFFA01 plugin;
    private final List<KitEntry> entries = new ArrayList<>();
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;

    public KitManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    /** One configured kit item and the slot it prefers. */
    public record KitEntry(int slot, ItemStack template) {
    }

    /** Re-parses the {@code kit} section of {@code config.yml}. */
    public void reload() {
        entries.clear();
        helmet = null;
        chestplate = null;
        leggings = null;
        boots = null;

        ConfigurationSection kit = plugin.getConfig().getConfigurationSection("kit");
        if (kit == null) {
            plugin.getLogger().warning("config.yml has no 'kit' section; players will spawn empty handed.");
            return;
        }

        ConfigurationSection inventory = kit.getConfigurationSection("inventory");
        if (inventory != null) {
            for (String key : inventory.getKeys(false)) {
                int slot = parseSlot(key);
                ConfigurationSection itemSection = inventory.getConfigurationSection(key);
                if (slot < 0 || itemSection == null) {
                    plugin.getLogger().warning("Skipping kit entry '" + key + "': not a valid inventory slot.");
                    continue;
                }
                ItemStack item = buildItem(itemSection);
                if (item != null) {
                    entries.add(new KitEntry(slot, item));
                }
            }
        }

        ConfigurationSection armor = kit.getConfigurationSection("armor");
        if (armor != null) {
            helmet = buildItem(armor.getConfigurationSection("helmet"));
            chestplate = buildItem(armor.getConfigurationSection("chestplate"));
            leggings = buildItem(armor.getConfigurationSection("leggings"));
            boots = buildItem(armor.getConfigurationSection("boots"));
        }
    }

    private int parseSlot(String key) {
        try {
            int slot = Integer.parseInt(key);
            return slot >= 0 && slot < 36 ? slot : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ItemStack buildItem(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String materialName = section.getString("material", "AIR");
        Material material = Material.matchMaterial(materialName == null ? "AIR" : materialName);
        if (material == null || material == Material.AIR) {
            if (materialName != null && !"AIR".equalsIgnoreCase(materialName)) {
                plugin.getLogger().warning("Unknown material '" + materialName + "' in the kit configuration.");
            }
            return null;
        }

        ItemStack item = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = section.getString("name");
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(infinode.org.example.bFFA01.util.Text.colorize(name));
        }
        if (section.getBoolean("unbreakable", true)) {
            meta.setUnbreakable(true);
        }

        ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
        if (enchantments != null) {
            for (String enchantName : enchantments.getKeys(false)) {
                Enchantment enchantment = Enchants.byName(enchantName);
                int level = enchantments.getInt(enchantName, 1);
                if (enchantment == null) {
                    plugin.getLogger().warning("Unknown enchantment '" + enchantName + "' in the kit configuration.");
                    continue;
                }
                if (level > 0) {
                    meta.addEnchant(enchantment, level, true);
                }
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Enchants the items a player is already carrying with a freshly bought
     * upgrade. 4.x handed out a whole new kit instead, which cleared the
     * inventory and destroyed every consumable the player had just paid for.
     */
    public void applyUpgrade(Player player, Upgrade upgrade) {
        Enchantment enchantment = Enchants.byName(upgrade.enchantmentKey());
        if (enchantment == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> candidates = new ArrayList<>();
        candidates.addAll(java.util.Arrays.asList(inventory.getContents()));
        candidates.addAll(java.util.Arrays.asList(inventory.getArmorContents()));

        for (ItemStack item : candidates) {
            if (item == null || !upgrade.target().matches(item.getType())) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                continue;
            }
            if (!meta.hasEnchant(enchantment) || meta.getEnchantLevel(enchantment) < upgrade.level()) {
                meta.addEnchant(enchantment, upgrade.level(), true);
                item.setItemMeta(meta);
            }
        }
        player.updateInventory();
    }

    /** Total arrows the kit hands out, used to refill after a kill. */
    public int arrowsInKit() {
        int arrows = 0;
        for (KitEntry entry : entries) {
            if (entry.template().getType() == Material.ARROW) {
                arrows += entry.template().getAmount();
            }
        }
        return arrows;
    }

    /**
     * The building block of the kit: the placeable material with the largest
     * stack. 4.x guessed this by checking whether the material name contained
     * "STONE", "BLOCK", "WOOD" or "PLANKS", which missed most block types.
     */
    public Material buildingBlock() {
        Material best = null;
        int bestAmount = 0;
        for (KitEntry entry : entries) {
            ItemStack template = entry.template();
            if (template.getType().isBlock() && template.getAmount() > bestAmount) {
                best = template.getType();
                bestAmount = template.getAmount();
            }
        }
        return best;
    }

    /** Tops up arrows and building blocks after a kill. */
    public void rewardAfterKill(Player player) {
        if (plugin.settings().refillArrowsOnKill()) {
            refill(player, Material.ARROW, arrowsInKit(), true);
        }
        int blocks = plugin.settings().blocksPerKill();
        Material block = buildingBlock();
        if (blocks > 0 && block != null) {
            refill(player, block, blocks, false);
        }
        player.updateInventory();
    }

    /**
     * @param exact {@code true} sets the stack to {@code amount},
     *              {@code false} adds {@code amount} up to a full stack
     */
    private void refill(Player player, Material material, int amount, boolean exact) {
        if (amount <= 0) {
            return;
        }
        boolean found = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != material) {
                continue;
            }
            found = true;
            if (exact) {
                item.setAmount(amount);
            } else {
                item.setAmount(Math.min(material.getMaxStackSize(), item.getAmount() + amount));
            }
        }
        if (!found) {
            player.getInventory().addItem(new ItemStack(material, amount));
        }
    }

    /** The configured kit entries, used by the kit editor preview. */
    public List<KitEntry> entries() {
        return new ArrayList<>(entries);
    }

    /** Clears the inventory and hands out the kit with upgrades and layout. */
    public void giveKit(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);

        PlayerData data = plugin.dataManager().get(player);
        List<KitEntry> pending = new ArrayList<>();
        for (KitEntry entry : entries) {
            pending.add(new KitEntry(entry.slot(), applyUpgrades(entry.template().clone(), data)));
        }

        // 1. Honour the player's saved hotbar layout.
        if (data.hasLayout()) {
            for (int slot = 0; slot < PlayerData.LAYOUT_SLOTS; slot++) {
                String wanted = data.layoutSlot(slot);
                if (wanted == null) {
                    continue;
                }
                KitEntry match = takeByMaterial(pending, wanted);
                if (match != null) {
                    inventory.setItem(slot, match.template());
                }
            }
        }

        // 2. Everything else goes to its configured slot, or the first free one.
        for (KitEntry entry : pending) {
            if (entry.slot() < inventory.getSize() && inventory.getItem(entry.slot()) == null) {
                inventory.setItem(entry.slot(), entry.template());
            } else {
                inventory.addItem(entry.template());
            }
        }

        inventory.setHelmet(applyUpgrades(clone(helmet), data));
        inventory.setChestplate(applyUpgrades(clone(chestplate), data));
        inventory.setLeggings(applyUpgrades(clone(leggings), data));
        inventory.setBoots(applyUpgrades(clone(boots), data));

        inventory.setHeldItemSlot(0);
        player.updateInventory();
    }

    private static ItemStack clone(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** Removes and returns the first pending entry of that material. */
    private KitEntry takeByMaterial(List<KitEntry> pending, String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            return null;
        }
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).template().getType() == material) {
                return pending.remove(i);
            }
        }
        return null;
    }

    /** Adds the enchantments from every upgrade the player owns. */
    private ItemStack applyUpgrades(ItemStack item, PlayerData data) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        boolean changed = false;
        for (Upgrade upgrade : Upgrade.values()) {
            if (!data.hasUpgrade(upgrade.id()) || !upgrade.target().matches(item.getType())) {
                continue;
            }
            Enchantment enchantment = Enchants.byName(upgrade.enchantmentKey());
            if (enchantment == null) {
                continue;
            }
            // Never downgrade an enchantment the kit configuration already set.
            if (!meta.hasEnchant(enchantment) || meta.getEnchantLevel(enchantment) < upgrade.level()) {
                meta.addEnchant(enchantment, upgrade.level(), true);
                changed = true;
            }
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
