package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.ui.StatsGui;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shows statistics as a menu for players and as text for the console. */
public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final BFFA01 plugin;

    public StatsCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PlayerData target;
        if (args.length > 0) {
            if (!sender.hasPermission("bffa.stats.other")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            target = online != null ? plugin.dataManager().get(online) : plugin.dataManager().findByName(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = plugin.dataManager().get(player);
        } else {
            plugin.messages().send(sender, "player-only");
            return true;
        }

        if (sender instanceof Player player) {
            new StatsGui(plugin, player, target).open();
        } else {
            printStats(sender, target);
        }
        return true;
    }

    private void printStats(CommandSender sender, PlayerData data) {
        int rank = plugin.dataManager().rankOf(data.uuid());
        sender.sendMessage(Text.colorize("&8&m                                        "));
        sender.sendMessage(Text.center("&b&l" + data.name()));
        sender.sendMessage(Text.colorize(" &7Kills: &a" + data.kills() + " &8| &7Deaths: &c" + data.deaths()
                + " &8| &7K/D: &6" + String.format("%.2f", data.kd())));
        sender.sendMessage(Text.colorize(" &7Coins: &6" + data.coins()
                + " &8| &7Best streak: &d" + data.bestStreak()
                + " &8| &7Rank: &b" + (rank > 0 ? "#" + rank : "-")));
        sender.sendMessage(Text.colorize("&8&m                                        "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("bffa.stats.other")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
