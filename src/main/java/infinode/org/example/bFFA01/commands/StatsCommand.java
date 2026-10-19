package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final BFFA01 plugin;

    public StatsCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(TextLines.render(plugin, "players-only", "&cOnly players can do that.", Map.of()));
            return true;
        }

        Player player = (Player) sender;
        if (plugin.presentation() != null && plugin.presentation().menus().open(player, "stats")) {
            return true;
        }
        int kills = plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager().getDeaths(player.getUniqueId());
        double kd = deaths == 0 ? kills : (double) kills / deaths;

        String ratio = String.format(Locale.US, "%.2f", kd);
        String coins = Integer.toString(plugin.getDataManager().getCoins(player.getUniqueId()));
        player.sendMessage(TextLines.render(plugin, "stats-header", "&7------ &bYour Stats &7------", Map.of()));
        player.sendMessage(TextLines.render(plugin, "stats-kills", "&7Kills: &a%kills%", Map.of("kills", Integer.toString(kills))));
        player.sendMessage(TextLines.render(plugin, "stats-deaths", "&7Deaths: &c%deaths%", Map.of("deaths", Integer.toString(deaths))));
        player.sendMessage(TextLines.render(plugin, "stats-kd", "&7K/D: &e%kd%", Map.of("kd", ratio)));
        player.sendMessage(TextLines.render(plugin, "stats-coins", "&7Coins: &6%coins%", Map.of("coins", coins)));
        player.sendMessage(TextLines.render(plugin, "stats-footer", "&7-------------------------", Map.of()));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
