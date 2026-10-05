package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.listeners.ShopInventoryHolder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class ShopCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public ShopCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;
        openShop(player);

        return true;
    }

    public void openShop(Player player) {
        Inventory inv = new ShopInventoryHolder().getInventory();
        int coins = plugin.getDataManager().getCoins(player.getUniqueId());

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.GOLD + "Your Coins: " + coins);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        // --- PERMANENT UPGRADES (Row 2) ---
        boolean hasSharpness = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "sharpness");
        inv.setItem(10, createGuiItem(Material.IRON_SWORD, ChatColor.AQUA + "Permanent Sharpness I", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "100 Coins",
            hasSharpness ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));

        boolean hasProtection = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "protection");
        inv.setItem(11, createGuiItem(Material.CHAINMAIL_CHESTPLATE, ChatColor.AQUA + "Permanent Protection I (Chest)", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "150 Coins",
            hasProtection ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));

        boolean hasPowerBow = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "powerbow");
        inv.setItem(12, createGuiItem(Material.BOW, ChatColor.AQUA + "Permanent Power I (Bow)", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "100 Coins",
            hasPowerBow ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));

        boolean hasKBStick = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "knockback2");
        inv.setItem(13, createGuiItem(Material.STICK, ChatColor.AQUA + "Permanent Knockback II Stick", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "150 Coins",
            hasKBStick ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));

        boolean hasFeatherFalling = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "featherfalling");
        inv.setItem(14, createGuiItem(Material.CHAINMAIL_BOOTS, ChatColor.AQUA + "Permanent Feather Falling II", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "120 Coins",
            hasFeatherFalling ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));

        boolean hasPunchBow = plugin.getDataManager().hasUpgrade(player.getUniqueId(), "punchbow");
        inv.setItem(15, createGuiItem(Material.ARROW, ChatColor.AQUA + "Permanent Punch I (Bow)", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "120 Coins",
            hasPunchBow ? ChatColor.GREEN + "Already Purchased!" : ChatColor.RED + "Click to buy"));


        // --- ONE-TIME PERKS / ITEMS (Rows 4 & 5) ---
        inv.setItem(28, createGuiItem(Material.GOLDEN_APPLE, ChatColor.LIGHT_PURPLE + "1x Golden Apple", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "10 Coins", ChatColor.RED + "Click to buy (Lost on death)"));

        inv.setItem(29, createGuiItem(Material.ENDER_PEARL, ChatColor.LIGHT_PURPLE + "1x Ender Pearl", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "25 Coins", ChatColor.RED + "Click to buy (Lost on death)"));

        inv.setItem(30, createGuiItem(Material.FISHING_ROD, ChatColor.LIGHT_PURPLE + "Grappling Hook", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "50 Coins", ChatColor.GRAY + "Use to pull yourself forward!", ChatColor.RED + "Click to buy (Lost on death)"));

        // Modified Item 1: Fireball (Increased Cost)
        inv.setItem(31, createGuiItem(Material.FIRE_CHARGE, ChatColor.RED + "Knockback Fireball", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "60 Coins", ChatColor.GRAY + "Right-click to shoot!", ChatColor.GRAY + "Massive knockback, huge visual explosion!", ChatColor.RED + "Click to buy (Lost on death)"));

        // New Item 2: Jump Boost Feather
        inv.setItem(32, createGuiItem(Material.FEATHER, ChatColor.YELLOW + "Jump Boost Feather", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "20 Coins", ChatColor.GRAY + "Right-click for Jump Boost III (10s)", ChatColor.RED + "Click to buy (One-time use)"));

        // New Item 3: Speed Powder
        inv.setItem(33, createGuiItem(Material.SUGAR, ChatColor.WHITE + "Speed Powder", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "20 Coins", ChatColor.GRAY + "Right-click for Speed III (5s)", ChatColor.RED + "Click to buy (One-time use)"));

        // New Item 4: Web Grenade
        inv.setItem(34, createGuiItem(Material.COBWEB, ChatColor.GRAY + "Web Grenade", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "40 Coins", ChatColor.GRAY + "Throws a web that traps players!", ChatColor.RED + "Click to buy (Lost on death)"));

        // New Item 5: Tracker Compass
        inv.setItem(37, createGuiItem(Material.COMPASS, ChatColor.GREEN + "Player Tracker", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "15 Coins", ChatColor.GRAY + "Points to the nearest player.", ChatColor.RED + "Click to buy (Lost on death)"));

        // New Item 6: Invisible Cloak
        inv.setItem(38, createGuiItem(Material.GLASS, ChatColor.DARK_GRAY + "Invisibility Cloak", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "60 Coins", ChatColor.GRAY + "Invisibility for 15s (No armor hidden)", ChatColor.RED + "Click to buy (One-time use)"));

        // New Item 7: Knockback TNT
        inv.setItem(39, createGuiItem(Material.TNT, ChatColor.RED + "Knockback TNT", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "40 Coins", ChatColor.GRAY + "Instantly explodes, huge knockback!", ChatColor.RED + "Click to buy (Lost on death)"));

        // New Item 8: Safe-Platform
        inv.setItem(40, createGuiItem(Material.SLIME_BLOCK, ChatColor.AQUA + "Rescue Platform", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "35 Coins", ChatColor.GRAY + "Spawns a 3x3 slime platform under you.", ChatColor.RED + "Click to buy (One-time use)"));

        // New Item 9: Vampire Fang
        inv.setItem(41, createGuiItem(Material.GHAST_TEAR, ChatColor.DARK_RED + "Vampire Fang", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "50 Coins", ChatColor.GRAY + "Next hit heals you fully!", ChatColor.RED + "Click to buy (One-time use)"));

        // New Item 10: Switcher Ball
        inv.setItem(42, createGuiItem(Material.SNOWBALL, ChatColor.LIGHT_PURPLE + "Switcher Ball", 
            ChatColor.GRAY + "Cost: " + ChatColor.GOLD + "45 Coins", ChatColor.GRAY + "Hit a player to swap positions!", ChatColor.RED + "Click to buy (Lost on death)"));

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
