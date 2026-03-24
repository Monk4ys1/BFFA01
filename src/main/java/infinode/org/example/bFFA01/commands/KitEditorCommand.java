package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class KitEditorCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public KitEditorCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;
        openEditor(player);

        return true;
    }

    public void openEditor(Player player) {
        // The player just sorts their hotbar.
        player.sendMessage(ChatColor.AQUA + "Sort your hotbar the way you like and type " + ChatColor.YELLOW + "/savekit" + ChatColor.AQUA + " to save it permanently.");
    }
}
