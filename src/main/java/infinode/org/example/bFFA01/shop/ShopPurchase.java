package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.MatchEvents;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Pays for one offer. The chest GUI and Bedrock forms both come through here.
 */
public final class ShopPurchase {

    private ShopPurchase() {
    }

    public static boolean buy(BFFA01 plugin, Player player, ShopOffer offer) {
        if (plugin == null || player == null || offer == null) {
            return false;
        }
        if (offer.permanent() && plugin.getDataManager().hasUpgrade(player.getUniqueId(), offer.id())) {
            player.sendMessage(TextLines.render(plugin, "shop-already-owned", "&cYou already own this upgrade!", Map.of()));
            return false;
        }
        CurrencyRouter currency = CurrencyRouter.get(plugin);
        if (!currency.canAfford(player, offer.cost()) || !currency.charge(player, offer.cost())) {
            player.sendMessage(TextLines.render(plugin, "shop-poor", "&cNot enough coins!", Map.of()));
            return false;
        }
        if (offer.permanent()) {
            plugin.getDataManager().addUpgrade(player.getUniqueId(), offer.id());
            plugin.getKitManager().giveKit(player);
        } else {
            player.getInventory().addItem(offer.icon(plugin, ShopOffer.key(plugin), false));
        }
        player.sendMessage(TextLines.render(plugin, "shop-purchased", "&aPurchased %item%!",
                Map.of("item", offer.legacyName())));
        MatchEvents.purchase(plugin, player, offer.id(), offer.cost(), offer.permanent());
        return true;
    }
}
