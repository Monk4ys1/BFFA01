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
        if (!event.getView().getTitle().equals("Shop & Upgrades")) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int coins = plugin.getDataManager().getCoins(player.getUniqueId());

        // Helper to check and buy upgrades
        if (handlePermanentUpgrade(player, clicked, Material.IRON_SWORD, "sharpness", 100, "Permanent Sharpness I")) return;
        if (handlePermanentUpgrade(player, clicked, Material.CHAINMAIL_CHESTPLATE, "protection", 150, "Permanent Protection I")) return;
        if (handlePermanentUpgrade(player, clicked, Material.BOW, "powerbow", 100, "Permanent Power I (Bow)")) return;
        if (handlePermanentUpgrade(player, clicked, Material.STICK, "knockback2", 150, "Permanent Knockback II")) return;
        if (handlePermanentUpgrade(player, clicked, Material.CHAINMAIL_BOOTS, "featherfalling", 120, "Permanent Feather Falling II")) return;
        if (handlePermanentUpgrade(player, clicked, Material.ARROW, "punchbow", 120, "Permanent Punch I (Bow)")) return;

        // One-time items
        if (clicked.getType() == Material.GOLDEN_APPLE) buyItem(player, clicked.getType(), "1x Golden Apple", 10, ChatColor.LIGHT_PURPLE + "1x Golden Apple");
        else if (clicked.getType() == Material.ENDER_PEARL) buyItem(player, clicked.getType(), "1x Ender Pearl", 25, ChatColor.LIGHT_PURPLE + "1x Ender Pearl");
        else if (clicked.getType() == Material.FISHING_ROD) buyItem(player, clicked.getType(), "Grappling Hook", 50, ChatColor.LIGHT_PURPLE + "Grappling Hook");
        else if (clicked.getType() == Material.FIRE_CHARGE) buyItem(player, clicked.getType(), "Knockback Fireball", 30, ChatColor.RED + "Knockback Fireball");
        else if (clicked.getType() == Material.FEATHER) buyItem(player, clicked.getType(), "Jump Boost Feather", 20, ChatColor.YELLOW + "Jump Boost Feather");
        else if (clicked.getType() == Material.SUGAR) buyItem(player, clicked.getType(), "Speed Powder", 20, ChatColor.WHITE + "Speed Powder");
        else if (clicked.getType() == Material.COBWEB) buyItem(player, clicked.getType(), "Web Grenade", 40, ChatColor.GRAY + "Web Grenade");
        else if (clicked.getType() == Material.COMPASS) buyItem(player, clicked.getType(), "Player Tracker", 15, ChatColor.GREEN + "Player Tracker");
        else if (clicked.getType() == Material.GLASS) buyItem(player, clicked.getType(), "Invisibility Cloak", 60, ChatColor.DARK_GRAY + "Invisibility Cloak");
        else if (clicked.getType() == Material.TNT) buyItem(player, clicked.getType(), "Knockback TNT", 40, ChatColor.RED + "Knockback TNT");
        else if (clicked.getType() == Material.SLIME_BLOCK) buyItem(player, clicked.getType(), "Rescue Platform", 35, ChatColor.AQUA + "Rescue Platform");
        else if (clicked.getType() == Material.GHAST_TEAR) buyItem(player, clicked.getType(), "Vampire Fang", 50, ChatColor.DARK_RED + "Vampire Fang");
        else if (clicked.getType() == Material.SNOWBALL) buyItem(player, clicked.getType(), "Switcher Ball", 45, ChatColor.LIGHT_PURPLE + "Switcher Ball");
    }

    private boolean handlePermanentUpgrade(Player player, ItemStack clicked, Material mat, String id, int cost, String name) {
        if (clicked.getType() == mat && clicked.getItemMeta().getDisplayName().contains(ChatColor.stripColor(name))) {
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
