package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.InventoryViews;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.shop.ShopPurchase;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ShopListener implements Listener {

    private final BFFA01 plugin;

    public ShopListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        Inventory top = InventoryViews.top(event);
        if (!(top != null && top.getHolder() instanceof ShopInventoryHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        NamespacedKey key = ShopOffer.key(plugin);
        ShopOffer offer = null;
        for (ShopOffer candidate : ShopOffer.values()) {
            if (candidate.matches(clicked, key)) {
                offer = candidate;
                break;
            }
        }
        if (offer == null) {
            return;
        }
        if (offer.permanent()) {
            buyUpgrade(player, offer);
        } else {
            buyItem(player, offer, key);
        }
    }

    private void buyUpgrade(Player player, ShopOffer offer) {
        if (ShopPurchase.buy(plugin, player, offer)) {
            player.closeInventory();
        }
    }

    private void buyItem(Player player, ShopOffer offer, NamespacedKey key) {
        ShopPurchase.buy(plugin, player, offer);
    }
}
