package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class MenuCommand implements CommandExecutor, TabCompleter {

    private final BFFA01 plugin;

    public MenuCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextLines.render(plugin, "players-only", "&cOnly players can do that.", Map.of()));
            return true;
        }
        String menu = switch (command.getName().toLowerCase()) {
            case "maps" -> "maps";
            case "settings" -> "settings";
            default -> "hub";
        };
        if (plugin.presentation() == null || !plugin.presentation().menus().open(player, menu)) {
            player.sendMessage(TextLines.render(plugin, "menu-missing", "&cThat menu is not configured.", Map.of()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
