package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.InventoryViews;
import infinode.org.example.bFFA01.shop.ShopCheckout;
import infinode.org.example.bFFA01.shop.ShopOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class ShopListener implements Listener {

    private final BFFA01 plugin;
    private final ShopCheckout checkout;

    public ShopListener(BFFA01 plugin) {
        this.plugin = plugin;
        this.checkout = new ShopCheckout(plugin);
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

        ShopOffer offer = ShopCheckout.offer(event.getCurrentItem(), ShopOffer.key(plugin));
        if (offer == null) {
            return;
        }
        ShopCheckout.Result result = checkout.buy(player, offer);
        if (result == ShopCheckout.Result.BOUGHT && offer.permanent()) {
            player.closeInventory();
        }
    }
}
