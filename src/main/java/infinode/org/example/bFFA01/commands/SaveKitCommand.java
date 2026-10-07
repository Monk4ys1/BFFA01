package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.LongSupplier;

public class SaveKitCommand implements CommandExecutor {

    static final long SAVE_COOLDOWN_NANOS = 3_000_000_000L;

    private final BFFA01 plugin;
    private final KeyedCooldown cooldown;
    private final LongSupplier clock;

    public SaveKitCommand(BFFA01 plugin) {
        this(plugin, new KeyedCooldown(SAVE_COOLDOWN_NANOS), System::nanoTime);
    }

    SaveKitCommand(BFFA01 plugin, KeyedCooldown cooldown, LongSupplier clock) {
        this.plugin = plugin;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;

        Player player = (Player) sender;
        long now = clock.getAsLong();
        if (cooldown.remainingMillis(player.getUniqueId(), now) > 0L) {
            player.sendMessage(ChatColor.RED + "Please wait a moment before saving your kit again.");
            return true;
        }

        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            hotbar[i] = player.getInventory().getItem(i);
        }

        if (!plugin.getDataManager().saveKitLayout(player.getUniqueId(), hotbar)) {
            player.sendMessage(ChatColor.RED + "Could not save your kit layout.");
            return true;
        }
        cooldown.markUsed(player.getUniqueId(), now);
        player.sendMessage(ChatColor.GREEN + "Your kit layout has been saved!");

        return true;
    }
}
