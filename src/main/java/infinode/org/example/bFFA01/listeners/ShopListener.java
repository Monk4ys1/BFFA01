package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ShopListener implements Listener {

    private final BFFA01 plugin;

    public ShopListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getItemMeta() == null) return;

        // Helper to check and buy upgrades
        if (handlePermanentUpgrade(player, clicked, Material.IRON_SWORD, "sharpness", 100, "Permanent Sharpness I")) return;
        if (handlePermanentUpgrade(player, clicked, Material.CHAINMAIL_CHESTPLATE, "protection", 150, "Permanent Protection I (Chest)")) return;
        if (handlePermanentUpgrade(player, clicked, Material.BOW, "powerbow", 100, "Permanent Power I (Bow)")) return;
        if (handlePermanentUpgrade(player, clicked, Material.STICK, "knockback2", 150, "Permanent Knockback II Stick")) return;
        if (handlePermanentUpgrade(player, clicked, Material.CHAINMAIL_BOOTS, "featherfalling", 120, "Permanent Feather Falling II")) return;
        if (handlePermanentUpgrade(player, clicked, Material.ARROW, "punchbow", 120, "Permanent Punch I (Bow)")) return;

        // One-time items. Prices match the amounts shown in the shop GUI.
        if (isShopItem(clicked, Material.GOLDEN_APPLE, ChatColor.LIGHT_PURPLE + "1x Golden Apple")) buyItem(player, clicked.getType(), "1x Golden Apple", 10, ChatColor.LIGHT_PURPLE + "1x Golden Apple");
        else if (isShopItem(clicked, Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "1x Ender Pearl")) buyItem(player, clicked.getType(), "1x Ender Pearl", 25, ChatColor.LIGHT_PURPLE + "1x Ender Pearl");
        else if (isShopItem(clicked, Material.FISHING_ROD, ChatColor.LIGHT_PURPLE + "Grappling Hook")) buyItem(player, clicked.getType(), "Grappling Hook", 50, ChatColor.LIGHT_PURPLE + "Grappling Hook");
        else if (isShopItem(clicked, Material.FIRE_CHARGE, ChatColor.RED + "Knockback Fireball")) buyItem(player, clicked.getType(), "Knockback Fireball", 60, ChatColor.RED + "Knockback Fireball");
        else if (isShopItem(clicked, Material.FEATHER, ChatColor.YELLOW + "Jump Boost Feather")) buyItem(player, clicked.getType(), "Jump Boost Feather", 20, ChatColor.YELLOW + "Jump Boost Feather");
        else if (isShopItem(clicked, Material.SUGAR, ChatColor.WHITE + "Speed Powder")) buyItem(player, clicked.getType(), "Speed Powder", 20, ChatColor.WHITE + "Speed Powder");
        else if (isShopItem(clicked, Material.COBWEB, ChatColor.GRAY + "Web Grenade")) buyItem(player, clicked.getType(), "Web Grenade", 40, ChatColor.GRAY + "Web Grenade");
        else if (isShopItem(clicked, Material.COMPASS, ChatColor.GREEN + "Player Tracker")) buyItem(player, clicked.getType(), "Player Tracker", 15, ChatColor.GREEN + "Player Tracker");
        else if (isShopItem(clicked, Material.GLASS, ChatColor.DARK_GRAY + "Invisibility Cloak")) buyItem(player, clicked.getType(), "Invisibility Cloak", 60, ChatColor.DARK_GRAY + "Invisibility Cloak");
        else if (isShopItem(clicked, Material.TNT, ChatColor.RED + "Knockback TNT")) buyItem(player, clicked.getType(), "Knockback TNT", 40, ChatColor.RED + "Knockback TNT");
        else if (isShopItem(clicked, Material.SLIME_BLOCK, ChatColor.AQUA + "Rescue Platform")) buyItem(player, clicked.getType(), "Rescue Platform", 35, ChatColor.AQUA + "Rescue Platform");
        else if (isShopItem(clicked, Material.GHAST_TEAR, ChatColor.DARK_RED + "Vampire Fang")) buyItem(player, clicked.getType(), "Vampire Fang", 50, ChatColor.DARK_RED + "Vampire Fang");
        else if (isShopItem(clicked, Material.SNOWBALL, ChatColor.LIGHT_PURPLE + "Switcher Ball")) buyItem(player, clicked.getType(), "Switcher Ball", 45, ChatColor.LIGHT_PURPLE + "Switcher Ball");
    }

    private boolean isShopItem(ItemStack clicked, Material mat, String displayName) {
        if (clicked.getType() != mat) return false;
        ItemMeta meta = clicked.getItemMeta();
        return meta != null && displayName.equals(meta.getDisplayName());
    }

    private boolean handlePermanentUpgrade(Player player, ItemStack clicked, Material mat, String id, int cost, String name) {
        ItemMeta meta = clicked.getItemMeta();
        String display = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";
        if (clicked.getType() == mat && name.equals(display)) {
            if (plugin.getDataManager().hasUpgrade(player.getUniqueId(), id)) {
                player.sendMessage(ChatColor.RED + "You already own this upgrade!");
                return true;
            }
            if (plugin.getDataManager().getCoins(player.getUniqueId()) >= cost) {
                plugin.getDataManager().removeCoins(player.getUniqueId(), cost);
                plugin.getDataManager().addUpgrade(player.getUniqueId(), id);
                player.sendMessage(ChatColor.GREEN + "Purchased " + name + "!");
                plugin.getKitManager().giveKit(player);
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "Not enough coins!");
            }
            return true;
        }
        return false;
    }

    private void buyItem(Player player, Material mat, String messageName, int cost, String displayName) {
        if (plugin.getDataManager().getCoins(player.getUniqueId()) >= cost) {
            plugin.getDataManager().removeCoins(player.getUniqueId(), cost);
            
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                item.setItemMeta(meta);
            }
            
            player.getInventory().addItem(item);
            player.sendMessage(ChatColor.GREEN + "Purchased " + messageName + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Not enough coins!");
        }
    }
}
