package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuAccessTest {

    @Test
    void buyAndOpenUseTheCommandNodes() {
        Player player = mock(Player.class);
        when(player.hasPermission("bffa.shop")).thenReturn(false);
        when(player.hasPermission("bffa.menu")).thenReturn(true);
        assertFalse(MenuAccess.allowed(player, "buy:apple"));
        assertTrue(MenuAccess.allowed(player, "close"));
        assertTrue(MenuAccess.allowed(player, "open:hub"));
        when(player.hasPermission("bffa.shop")).thenReturn(true);
        assertTrue(MenuAccess.allowed(player, "buy:apple"));
    }
}
