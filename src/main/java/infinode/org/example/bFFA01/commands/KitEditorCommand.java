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

public class KitEditorCommand implements CommandExecutor, TabCompleter {

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
        if (plugin.presentation() != null && plugin.presentation().menus().open(player, "kit")) {
            return;
        }
        player.sendMessage(TextLines.render(plugin, "kit-editor",
                "&bSort your hotbar the way you like and type &e/savekit &bto save it permanently.", Map.of()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
