package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetMapCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public SetMapCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("bffa.admin")) {
            String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
            String noPerm = plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission to do that.");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + noPerm));
            return true;
        }

        if (args.length != 1) {
            String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
            String usage = plugin.getConfig().getString("messages.command-usage", "&cUsage: /bffa <setmap> <mapname>");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + usage));
            return true;
        }

        String mapName = args[0];
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
        if (!MapNames.isValid(mapName)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix) + ChatColor.RED + "Map name must be 1-32 letters, numbers, underscores, or hyphens.");
            return true;
        }
        if (!plugin.getMapManager().defineSpawn(mapName, player.getLocation())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix) + ChatColor.RED + "Could not save that map spawn.");
            return true;
        }

        String success = plugin.getConfig().getString("messages.map-set", "&aSpawn location for map &e%map% &aset successfully.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + success.replace("%map%", MapNames.sanitizeLabel(mapName))));

        return true;
    }
}
