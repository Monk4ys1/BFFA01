package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Toggles build mode, which bypasses arena protection so a map can be edited
 * while the server is running. The documentation promised this command in 4.x,
 * but it did not exist.
 */
public final class BuildCommand implements CommandExecutor, TabCompleter {

    private final BFFA01 plugin;

    public BuildCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length > 0) {
            if (!sender.hasPermission("bffa.admin")) {
                plugin.messages().send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[0]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messages().send(sender, "player-only");
            return true;
        }

        boolean enabled = plugin.buildMode().toggle(target);
        plugin.messages().send(target, enabled ? "build-mode-on" : "build-mode-off");
        if (!target.equals(sender)) {
            plugin.messages().send(sender, enabled ? "build-mode-other-on" : "build-mode-other-off",
                    "player", target.getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission("bffa.admin")) {
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
