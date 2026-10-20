package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.entity.Player;

/**
 * Pays for one offer. Chest menus and Floodgate forms both come through {@link ShopCheckout}.
 */
public final class ShopPurchase {

    private ShopPurchase() {
    }

    public static boolean buy(BFFA01 plugin, Player player, ShopOffer offer) {
        if (plugin == null) {
            return false;
        }
        return new ShopCheckout(plugin).buy(player, offer) == ShopCheckout.Result.BOUGHT;
    }
}
