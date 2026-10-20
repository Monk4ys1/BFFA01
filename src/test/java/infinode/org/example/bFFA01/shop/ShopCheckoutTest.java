package infinode.org.example.bFFA01.shop;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.managers.DataManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopCheckoutTest {

    @Test
    void aFullInventoryReturnsTheCoins() {
        UUID id = UUID.randomUUID();
        BFFA01 plugin = mock(BFFA01.class);
        DataManager data = mock(DataManager.class);
        when(plugin.getDataManager()).thenReturn(data);
        when(data.getCoins(id)).thenReturn(100);
        when(data.removeCoins(id, ShopOffer.APPLE.cost())).thenReturn(true);
        when(data.addCoins(id, ShopOffer.APPLE.cost())).thenReturn(true);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, new ItemStack(Material.GOLDEN_APPLE));
        when(inventory.addItem(any(ItemStack.class))).thenReturn(leftover);

        ShopCheckout.Result result = new ShopCheckout(plugin).buy(player, ShopOffer.APPLE);

        assertEquals(ShopCheckout.Result.FULL, result);
        verify(data).removeCoins(id, ShopOffer.APPLE.cost());
        verify(data).addCoins(id, ShopOffer.APPLE.cost());
    }
}
