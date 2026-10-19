package infinode.org.example.bFFA01.hooks.vault;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.managers.DataManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
