package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.MatchEvents;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Shared purchase path for the chest shop and any older shop inventory.
 */
public final class ShopCheckout {

    public enum Result {
        BOUGHT,
        OWNED,
        POOR,
        MISSING
    }

    private final BFFA01 plugin;

    public ShopCheckout(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public Result buy(Player player, ShopOffer offer) {
        if (player == null || offer == null) {
            return Result.MISSING;
        }
        if (plugin.getDataManager() == null) {
            return Result.MISSING;
        }
        if (offer.permanent() && plugin.getDataManager().hasUpgrade(player.getUniqueId(), offer.id())) {
            player.sendMessage(TextLines.render(plugin, "shop-already-owned", "&cYou already own this upgrade!", Map.of()));
            return Result.OWNED;
        }
        CurrencyRouter currency = CurrencyRouter.get(plugin);
        if (!currency.canAfford(player, offer.cost()) || !currency.charge(player, offer.cost())) {
            player.sendMessage(TextLines.render(plugin, "shop-poor", "&cNot enough coins!", Map.of()));
            return Result.POOR;
        }
        if (offer.permanent()) {
            plugin.getDataManager().addUpgrade(player.getUniqueId(), offer.id());
            if (plugin.getKitManager() != null) {
                plugin.getKitManager().giveKit(player);
            }
        } else if (player.isOnline()) {
            player.getInventory().addItem(offer.icon(plugin, ShopOffer.key(plugin), false));
        }
        player.sendMessage(TextLines.render(plugin, "shop-purchased", "&aPurchased %item%!",
                Map.of("item", offer.legacyName())));
        MatchEvents.purchase(plugin, player, offer.id(), offer.cost(), offer.permanent());
        return Result.BOUGHT;
    }

    public static ShopOffer offer(org.bukkit.inventory.ItemStack stack, NamespacedKey key) {
        if (stack == null) {
            return null;
        }
        for (ShopOffer candidate : ShopOffer.values()) {
            if (candidate.matches(stack, key)) {
                return candidate;
            }
        }
        return null;
    }
}
