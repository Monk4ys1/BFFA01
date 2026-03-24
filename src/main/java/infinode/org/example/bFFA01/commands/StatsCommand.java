package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public StatsCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;
        int kills = plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager().getDeaths(player.getUniqueId());
        double kd = deaths == 0 ? kills : (double) kills / deaths;

        player.sendMessage(ChatColor.GRAY + "------ " + ChatColor.AQUA + "Your Stats" + ChatColor.GRAY + " ------");
        player.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + kills);
        player.sendMessage(ChatColor.GRAY + "Deaths: " + ChatColor.RED + deaths);
        player.sendMessage(ChatColor.GRAY + "K/D: " + ChatColor.YELLOW + String.format("%.2f", kd));
        player.sendMessage(ChatColor.GRAY + "Coins: " + ChatColor.GOLD + plugin.getDataManager().getCoins(player.getUniqueId()));
        player.sendMessage(ChatColor.GRAY + "-------------------------");

        return true;
    }
}
