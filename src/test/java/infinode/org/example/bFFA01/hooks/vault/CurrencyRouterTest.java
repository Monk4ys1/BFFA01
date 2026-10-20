package infinode.org.example.bFFA01.hooks.vault;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.managers.DataManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrencyRouterTest {

    @Test
    void internalKillRewardUsesTheConfiguredAmount() {
        UUID id = UUID.randomUUID();
        YamlConfiguration config = new YamlConfiguration();
        config.set("integrations.vault.kill-reward", "internal");
        config.set("integrations.vault.kill-amount", 5);
        BFFA01 plugin = plugin(config);
        DataManager data = mock(DataManager.class);
        when(plugin.getDataManager()).thenReturn(data);
        Player player = player(id);

        new CurrencyRouter(plugin).rewardKill(player);

        verify(data).addCoins(id, 5);
    }

    @Test
    void vaultShopWithoutAnEconomyCannotBeCharged() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("integrations.vault.shop", "vault");
        BFFA01 plugin = plugin(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("bffa-test"));
        Player player = player(UUID.randomUUID());

        CurrencyRouter router = new CurrencyRouter(plugin);
        assertFalse(router.canAfford(player, 10));
        assertFalse(router.charge(player, 10));
    }

    @Test
    void vaultShopBalanceIsTheEconomyBalance() {
        UUID id = UUID.randomUUID();
        YamlConfiguration config = new YamlConfiguration();
        config.set("integrations.vault.shop", "vault");
        BFFA01 plugin = plugin(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("bffa-test"));
        CurrencyRouter router = new CurrencyRouter(plugin);
        router.vault(new VaultBridge() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public boolean has(UUID player, int amount) {
                return true;
            }

            @Override
            public boolean withdraw(UUID player, int amount) {
                return true;
            }

            @Override
            public boolean deposit(UUID player, int amount) {
                return true;
            }

            @Override
            public String formatted(UUID player) {
                return "$12";
            }

            @Override
            public String chatPrefix(UUID player) {
                return "";
            }
        });
        assertEquals("$12", router.balance(player(id)));
    }

    @Test
    void negativeCostDoesNotMoveCoins() {
        UUID id = UUID.randomUUID();
        BFFA01 plugin = plugin(new YamlConfiguration());
        DataManager data = mock(DataManager.class);
        when(plugin.getDataManager()).thenReturn(data);
        when(data.getCoins(id)).thenReturn(50);
        Player player = player(id);
        CurrencyRouter router = new CurrencyRouter(plugin);

        assertFalse(router.canAfford(player, -5));
        assertFalse(router.charge(player, -5));
        assertFalse(router.refund(player, -5));
        verify(data, never()).removeCoins(any(), anyInt());
        verify(data, never()).addCoins(any(), anyInt());
    }

    @Test
    void zeroCostDoesNotTouchEitherBalance() {
        BFFA01 plugin = plugin(new YamlConfiguration());
        Player player = player(UUID.randomUUID());
        CurrencyRouter router = new CurrencyRouter(plugin);
        assertTrue(router.canAfford(player, 0));
        assertTrue(router.charge(player, 0));
    }

    private static BFFA01 plugin(YamlConfiguration config) {
        BFFA01 plugin = mock(BFFA01.class);
        when(plugin.getConfig()).thenReturn(config);
        return plugin;
    }

    private static Player player(UUID id) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        return player;
    }
}
