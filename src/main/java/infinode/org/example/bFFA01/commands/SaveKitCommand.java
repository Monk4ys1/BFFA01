package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.TextLines;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public class SaveKitCommand implements CommandExecutor, TabCompleter {

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
            player.sendMessage(TextLines.render(plugin, "kit-save-cooldown",
                    "&cPlease wait a moment before saving your kit again.", Map.of()));
            return true;
        }

        ItemStack[] hotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            hotbar[i] = player.getInventory().getItem(i);
        }

        if (!plugin.getDataManager().saveKitLayout(player.getUniqueId(), hotbar)) {
            player.sendMessage(TextLines.render(plugin, "kit-save-failed", "&cCould not save your kit layout.", Map.of()));
            return true;
        }
        cooldown.markUsed(player.getUniqueId(), now);
        player.sendMessage(TextLines.render(plugin, "kit-saved", "&aYour kit layout has been saved!", Map.of()));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
