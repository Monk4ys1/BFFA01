package infinode.org.example.bFFA01.util;

import infinode.org.example.bFFA01.compat.Enchants;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small fluent builder so GUI code reads as a description of the icon instead
 * of six lines of meta juggling.
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, Math.max(1, amount));
        this.meta = this.item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(Text.colorize(name));
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        if (meta != null && lines != null) {
            meta.setLore(Text.colorize(lines));
        }
        return this;
    }

    public ItemBuilder addLore(String line) {
        if (meta == null) {
            return this;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add(Text.colorize(line));
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null && enchantment != null && level > 0) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /** Adds the enchantment shimmer without showing an enchantment in the lore. */
    public ItemBuilder glow(boolean glowing) {
        if (!glowing || meta == null) {
            return this;
        }
        Enchantment marker = anyEnchantment();
        if (marker != null) {
            meta.addEnchant(marker, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    private static Enchantment anyEnchantment() {
        Enchantment unbreaking = Enchants.byName("unbreaking");
        return unbreaking != null ? unbreaking : Enchants.sharpness();
    }

    /** Hides attribute lines so weapons in the shop stay readable. */
    public ItemBuilder hideAttributes() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }
        return this;
    }

    public ItemBuilder unbreakable() {
        if (meta != null) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
        return this;
    }

    public ItemBuilder skullOf(OfflinePlayer owner) {
        if (meta instanceof SkullMeta skullMeta && owner != null) {
            skullMeta.setOwningPlayer(owner);
        }
        return this;
    }

    /**
     * Tags the item so listeners can identify it without comparing display
     * names, which breaks as soon as an admin renames it in the config.
     */
    public ItemBuilder tag(NamespacedKey key, String value) {
        if (meta != null && key != null && value != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Reads a tag written by {@link #tag(NamespacedKey, String)}. */
    public static String readTag(ItemStack stack, NamespacedKey key) {
        if (stack == null || key == null || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta itemMeta = stack.getItemMeta();
        if (itemMeta == null) {
            return null;
        }
        return itemMeta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
