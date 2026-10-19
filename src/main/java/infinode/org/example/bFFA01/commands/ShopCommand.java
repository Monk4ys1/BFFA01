package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.listeners.ShopInventoryHolder;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final BFFA01 plugin;

    public ShopCommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextLines.render(plugin, "players-only", "&cOnly players can do that.", Map.of()));
            return true;
        }
        openShop(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    public void openShop(Player player) {
        if (plugin.presentation() != null && plugin.presentation().menus().open(player, "shop")) {
            return;
        }
        Inventory inv = new ShopInventoryHolder().getInventory();
        int coins = plugin.getDataManager().getCoins(player.getUniqueId());

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(TextLines.render(plugin, "shop-coins", "&6Your Coins: &e%coins%",
                    Map.of("coins", Integer.toString(coins))));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        NamespacedKey key = ShopOffer.key(plugin);
        for (ShopOffer offer : ShopOffer.values()) {
            boolean owned = offer.permanent() && plugin.getDataManager().hasUpgrade(player.getUniqueId(), offer.id());
            inv.setItem(offer.slot(), offer.icon(plugin, key, owned));
        }

        player.openInventory(inv);
    }
}
