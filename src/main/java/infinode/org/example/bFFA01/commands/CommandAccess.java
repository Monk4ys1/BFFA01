package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.TextLines;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Player commands re-check their permission node and share a short pace.
 * Bukkit already blocks a command whose plugin.yml permission is missing;
 * the check here also covers a menu action that opens the same screen.
 */
public final class CommandAccess {

    private static final KeyedCooldown PACE = new KeyedCooldown(400_000_000L);

    private CommandAccess() {
    }

    public static boolean refuse(BFFA01 plugin, CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            if (sender != null) {
                sender.sendMessage(TextLines.render(plugin, "players-only", "&cOnly players can do that.", Map.of()));
            }
            return true;
        }
        if (deny(plugin, player, permission)) {
            return true;
        }
        long now = System.nanoTime();
        if (PACE.remainingMillis(player.getUniqueId(), now) > 0L) {
            player.sendMessage(TextLines.render(plugin, "command-cooldown", "&cPlease wait a moment.", Map.of()));
            return true;
        }
        PACE.markUsed(player.getUniqueId(), now);
        return false;
    }

    public static boolean deny(BFFA01 plugin, CommandSender sender, String permission) {
        if (sender == null || permission == null || sender.hasPermission(permission)) {
            return sender == null;
        }
        sender.sendMessage(TextLines.render(plugin, "no-permission", "&cYou do not have permission to do that.", Map.of()));
        return true;
    }
}
