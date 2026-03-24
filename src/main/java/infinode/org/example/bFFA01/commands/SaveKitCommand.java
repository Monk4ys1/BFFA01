package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SaveKitCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public SaveKitCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;
        ItemStack[] hotbar = new ItemStack[9];
        
        for (int i = 0; i < 9; i++) {
            hotbar[i] = player.getInventory().getItem(i);
        }

        plugin.getDataManager().saveKitLayout(player.getUniqueId(), hotbar);
        player.sendMessage(ChatColor.GREEN + "Your kit layout has been saved!");

        return true;
    }
}
