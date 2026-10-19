package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.Messages;
import infinode.org.example.bFFA01.text.MiniText;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One catalog for the shop GUI, the click handler, and item abilities.
 * Identity is a persistent-data id, with the old English display name as a fallback.
 */
public enum ShopOffer {
    SHARPNESS("sharpness", 10, Material.IRON_SWORD, 100, true, ChatColor.AQUA + "Permanent Sharpness I", "shop-click-buy", null),
    PROTECTION("protection", 11, Material.CHAINMAIL_CHESTPLATE, 150, true, ChatColor.AQUA + "Permanent Protection I (Chest)", "shop-click-buy", null),
    POWER("powerbow", 12, Material.BOW, 100, true, ChatColor.AQUA + "Permanent Power I (Bow)", "shop-click-buy", null),
    KNOCKBACK("knockback2", 13, Material.STICK, 150, true, ChatColor.AQUA + "Permanent Knockback II Stick", "shop-click-buy", null),
    FEATHER("featherfalling", 14, Material.CHAINMAIL_BOOTS, 120, true, ChatColor.AQUA + "Permanent Feather Falling II", "shop-click-buy", null),
    PUNCH("punchbow", 15, Material.ARROW, 120, true, ChatColor.AQUA + "Permanent Punch I (Bow)", "shop-click-buy", null),
    APPLE("apple", 28, Material.GOLDEN_APPLE, 10, false, ChatColor.LIGHT_PURPLE + "1x Golden Apple", "shop-click-lost", null),
    PEARL("pearl", 29, Material.ENDER_PEARL, 25, false, ChatColor.LIGHT_PURPLE + "1x Ender Pearl", "shop-click-lost", null),
    GRAPPLE("grapple", 30, Material.FISHING_ROD, 50, false, ChatColor.LIGHT_PURPLE + "Grappling Hook", "shop-click-lost", "shop-grapple-hint"),
    FIREBALL("fireball", 31, Material.FIRE_CHARGE, 60, false, ChatColor.RED + "Knockback Fireball", "shop-click-lost", "shop-fireball-hint"),
    JUMP("jump", 32, Material.FEATHER, 20, false, ChatColor.YELLOW + "Jump Boost Feather", "shop-click-once", "shop-jump-hint"),
    SPEED("speed", 33, Material.SUGAR, 20, false, ChatColor.WHITE + "Speed Powder", "shop-click-once", "shop-speed-hint"),
    WEB("web", 34, Material.COBWEB, 40, false, ChatColor.GRAY + "Web Grenade", "shop-click-lost", "shop-web-hint"),
    TRACKER("tracker", 37, Material.COMPASS, 15, false, ChatColor.GREEN + "Player Tracker", "shop-click-lost", "shop-tracker-hint"),
    INVIS("invis", 38, Material.GLASS, 60, false, ChatColor.DARK_GRAY + "Invisibility Cloak", "shop-click-once", "shop-invis-hint"),
    TNT("tnt", 39, Material.TNT, 40, false, ChatColor.RED + "Knockback TNT", "shop-click-lost", "shop-tnt-hint"),
    PLATFORM("platform", 40, Material.SLIME_BLOCK, 35, false, ChatColor.AQUA + "Rescue Platform", "shop-click-once", "shop-platform-hint"),
    VAMPIRE("vampire", 41, Material.GHAST_TEAR, 50, false, ChatColor.DARK_RED + "Vampire Fang", "shop-click-once", "shop-vampire-hint"),
    SWITCHER("switcher", 42, Material.SNOWBALL, 45, false, ChatColor.LIGHT_PURPLE + "Switcher Ball", "shop-click-lost", "shop-switcher-hint");

    private final String id;
    private final int slot;
    private final Material material;
    private final int cost;
    private final boolean permanent;
    private final String legacyName;
    private final String actionKey;
    private final String hintKey;

    ShopOffer(String id, int slot, Material material, int cost, boolean permanent, String legacyName, String actionKey, String hintKey) {
        this.id = id;
        this.slot = slot;
        this.material = material;
        this.cost = cost;
        this.permanent = permanent;
        this.legacyName = legacyName;
        this.actionKey = actionKey;
        this.hintKey = hintKey;
    }

    public String id() {
        return id;
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public int cost() {
        return cost;
    }

    public boolean permanent() {
        return permanent;
    }

    public String legacyName() {
        return legacyName;
    }

    public String actionKey() {
        return actionKey;
    }

    public String hintKey() {
        return hintKey;
    }

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, "shop_item");
    }

    public static ShopOffer byId(String id) {
        if (id == null) {
            return null;
        }
        for (ShopOffer offer : values()) {
            if (offer.id.equals(id)) {
                return offer;
            }
        }
        return null;
    }

    public boolean matches(ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.getType() != material || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (key != null) {
            String stored = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (id.equals(stored)) {
                return true;
            }
        }
        return legacyName.equals(meta.getDisplayName());
    }

    public ItemStack icon(BFFA01 plugin, NamespacedKey key, boolean owned) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        Messages messages = plugin.getMessages();
        String name = messages == null
                ? legacyName
                : messages.render("shop-" + id + "-name", Map.of());
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        String costLine = messages == null
                ? MiniText.render("<gray>Cost: <gold>" + cost + " Coins", Map.of())
                : messages.render("shop-cost", Map.of("cost", Integer.toString(cost)));
        lore.add(costLine);
        if (hintKey != null && messages != null) {
            lore.add(messages.render(hintKey, Map.of()));
        }
        String action = messages == null ? "" : messages.render(owned && permanent ? "shop-owned" : actionKey, Map.of());
        if (!action.isEmpty()) {
            lore.add(action);
        }
        meta.setLore(lore);
        if (key != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        }
        item.setItemMeta(meta);
        return item;
    }
}
