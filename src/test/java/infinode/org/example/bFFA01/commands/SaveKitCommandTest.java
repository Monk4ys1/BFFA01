package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.managers.DataManager;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveKitCommandTest {

    @Test
    void secondSaveInsideTheCooldownDoesNotWrite() {
        UUID playerId = UUID.randomUUID();
        AtomicLong now = new AtomicLong(10_000L);
        DataManager data = mock(DataManager.class);
        when(data.saveKitLayout(eq(playerId), any())).thenReturn(true);
        Player player = player(playerId);
        SaveKitCommand command = command(data, new KeyedCooldown(3_000L), now, player);

        command.onCommand(player, null, "savekit", new String[0]);
        command.onCommand(player, null, "savekit", new String[0]);

        verify(data, times(1)).saveKitLayout(eq(playerId), any());
        verify(player).sendMessage(contains("Please wait a moment"));
    }

    @Test
    void cooldownMessageUsesTheCommandClock() {
        UUID playerId = UUID.randomUUID();
        AtomicLong now = new AtomicLong(10_000L);
        DataManager data = mock(DataManager.class);
        when(data.saveKitLayout(eq(playerId), any())).thenReturn(true);
        Player player = player(playerId);
        SaveKitCommand command = command(data, new KeyedCooldown(SaveKitCommand.SAVE_COOLDOWN_MILLIS), now, player);

        command.onCommand(player, null, "savekit", new String[0]);
        command.onCommand(player, null, "savekit", new String[0]);
        now.addAndGet(SaveKitCommand.SAVE_COOLDOWN_MILLIS);
        command.onCommand(player, null, "savekit", new String[0]);

        verify(player).sendMessage(contains("Please wait a moment"));
        verify(data, times(2)).saveKitLayout(eq(playerId), any());
    }

    @Test
    void failedSaveDoesNotStartTheCooldown() {
        UUID playerId = UUID.randomUUID();
        AtomicLong now = new AtomicLong(10_000L);
        DataManager data = mock(DataManager.class);
        when(data.saveKitLayout(eq(playerId), any())).thenReturn(false, true);
        Player player = player(playerId);
        SaveKitCommand command = command(data, new KeyedCooldown(SaveKitCommand.SAVE_COOLDOWN_MILLIS), now, player);

        command.onCommand(player, null, "savekit", new String[0]);
        command.onCommand(player, null, "savekit", new String[0]);

        verify(data, times(2)).saveKitLayout(eq(playerId), any());
        verify(player).sendMessage(contains("Could not save"));
        verify(player).sendMessage(contains("has been saved"));
    }

    @Test
    void cooldownConstantStaysShort() {
        assertEquals(3_000L, SaveKitCommand.SAVE_COOLDOWN_MILLIS);
    }

    private static SaveKitCommand command(DataManager data, UUID playerId, AtomicLong now) {
        return command(data, new KeyedCooldown(3_000L), now, player(playerId));
    }

    private static SaveKitCommand command(DataManager data, KeyedCooldown cooldown, AtomicLong now, Player ignored) {
        BFFA01 plugin = mock(BFFA01.class);
        when(plugin.getDataManager()).thenReturn(data);
        return new SaveKitCommand(plugin, cooldown, now::get);
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);
        return player;
    }
}
