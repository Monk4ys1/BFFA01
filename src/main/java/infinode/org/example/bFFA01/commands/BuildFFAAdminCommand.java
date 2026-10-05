package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BuildFFAAdminCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public BuildFFAAdminCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bffa.admin")) {
            String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
            String noPerm = plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission to do that.");
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + noPerm));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] "));

        switch (subCommand) {
            case "setmap":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can set map spawns.");
                    return true;
                }
                if (args.length != 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /bffa setmap <mapname>");
                    return true;
                }
                
                Player player = (Player) sender;
                String mapName = args[1];
                if (!MapNames.isValid(mapName)) {
                    sender.sendMessage(prefix + ChatColor.RED + "Map name must be 1-32 letters, numbers, underscores, or hyphens.");
                    return true;
                }
                if (!plugin.getMapManager().defineSpawn(mapName, player.getLocation())) {
                    sender.sendMessage(prefix + ChatColor.RED + "Could not save that map spawn.");
                    return true;
                }

                String success = plugin.getConfig().getString("messages.map-set", "&aSpawn location for map &e%map% &aset successfully.");
                sender.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', success.replace("%map%", MapNames.sanitizeLabel(mapName))));
                break;

            case "addcoins":
                if (args.length != 3) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /bffa addcoins <player> <amount>");
                    return true;
                }
                Player targetAdd = Bukkit.getPlayer(args[1]);
                if (targetAdd == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                Integer amount = parsePositiveAmount(sender, args[2], prefix);
                if (amount == null) {
                    return true;
                }
                plugin.getDataManager().addCoins(targetAdd.getUniqueId(), amount);
                sender.sendMessage(prefix + ChatColor.GREEN + "Gave " + amount + " coins to " + targetAdd.getName() + ".");
                targetAdd.sendMessage(prefix + ChatColor.GREEN + "You received " + amount + " coins!");
                break;

            case "removecoins":
                if (args.length != 3) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /bffa removecoins <player> <amount>");
                    return true;
                }
                Player targetRem = Bukkit.getPlayer(args[1]);
                if (targetRem == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                Integer removeAmount = parsePositiveAmount(sender, args[2], prefix);
                if (removeAmount == null) {
                    return true;
                }
                plugin.getDataManager().removeCoins(targetRem.getUniqueId(), removeAmount);
                sender.sendMessage(prefix + ChatColor.GREEN + "Removed " + removeAmount + " coins from " + targetRem.getName() + ".");
                break;

            case "resetstats":
                if (args.length != 2) {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /bffa resetstats <player>");
                    return true;
                }
                Player targetReset = Bukkit.getPlayer(args[1]);
                if (targetReset == null) {
                    sender.sendMessage(prefix + ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                plugin.getDataManager().getConfig().set(targetReset.getUniqueId() + ".kills", 0);
                plugin.getDataManager().getConfig().set(targetReset.getUniqueId() + ".deaths", 0);
                plugin.getDataManager().saveData();
                sender.sendMessage(prefix + ChatColor.GREEN + "Reset stats for " + targetReset.getName() + ".");
                break;
                
            case "swapmap":
                if (args.length == 1) {
                    plugin.getMapManager().forceSwapSequence(null);
                    sender.sendMessage(prefix + ChatColor.GREEN + "Forced a map swap. Sequence initiated (3 seconds).");
                } else if (args.length == 2) {
                    String targetMap = args[1];
                    if (!plugin.getMapManager().hasMap(targetMap)) {
                        sender.sendMessage(prefix + ChatColor.RED + "Unknown map.");
                        return true;
                    }
                    plugin.getMapManager().forceSwapSequence(targetMap);
                    sender.sendMessage(prefix + ChatColor.GREEN + "Forced a map swap to '" + MapNames.sanitizeLabel(targetMap) + "'. Sequence initiated (3 seconds).");
                } else {
                    sender.sendMessage(prefix + ChatColor.RED + "Usage: /bffa swapmap [mapname]");
                }
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private Integer parsePositiveAmount(CommandSender sender, String raw, String prefix) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount <= 0) {
                sender.sendMessage(prefix + ChatColor.RED + "Amount must be a positive number.");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage(prefix + ChatColor.RED + "Invalid amount.");
            return null;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "--- BuildFFA Admin Commands ---");
        sender.sendMessage(ChatColor.AQUA + "/bffa setmap <name>" + ChatColor.GRAY + " - Set map spawn");
        sender.sendMessage(ChatColor.AQUA + "/bffa addcoins <player> <amount>" + ChatColor.GRAY + " - Give coins");
        sender.sendMessage(ChatColor.AQUA + "/bffa removecoins <player> <amount>" + ChatColor.GRAY + " - Remove coins");
        sender.sendMessage(ChatColor.AQUA + "/bffa resetstats <player>" + ChatColor.GRAY + " - Reset kills/deaths");
        sender.sendMessage(ChatColor.AQUA + "/bffa swapmap [name]" + ChatColor.GRAY + " - Force swap map (with 3s sequence)");
    }
}
