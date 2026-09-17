package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Saves the player's current hotbar order as their kit layout.
 * Kept from 4.x because players have the muscle memory for it; the menu in
 * {@code /kit} does the same thing with a preview.
 */
public final class SaveKitCommand implements CommandExecutor {

    private final BFFA01 plugin;

    public SaveKitCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        PlayerData data = plugin.dataManager().get(player);
        data.clearLayout();
        for (int slot = 0; slot < PlayerData.LAYOUT_SLOTS; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                data.layoutSlot(slot, item.getType().name());
            }
        }
        plugin.dataManager().markDirty();
        plugin.messages().send(player, "kit-saved");
        return true;
    }
}
