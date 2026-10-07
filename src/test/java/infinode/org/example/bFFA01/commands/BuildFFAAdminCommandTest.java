package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.managers.DataManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildFFAAdminCommandTest {

    private final UUID notchId = UUID.randomUUID();
    private DataManager data;
    private BuildFFAAdminCommand command;
    private CommandSender sender;
    private Player notch;

    @BeforeEach
    void setUp() {
        BFFA01 plugin = mock(BFFA01.class);
        data = mock(DataManager.class);
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataManager()).thenReturn(data);
        when(data.addCoins(any(), anyInt())).thenReturn(true);
        when(data.removeCoins(any(), anyInt())).thenReturn(true);
        when(data.resetStats(any())).thenReturn(true);

        notch = mock(Player.class);
        when(notch.getUniqueId()).thenReturn(notchId);
        when(notch.getName()).thenReturn("Notch");

        sender = mock(CommandSender.class);
        when(sender.hasPermission("bffa.admin")).thenReturn(true);
        command = new BuildFFAAdminCommand(plugin, name -> "Notch".equalsIgnoreCase(name) ? notch : null);
    }

    @Test
    void partialNameDoesNotMatchAnOnlinePlayer() {
        command.onCommand(sender, null, "bffa", new String[]{"addcoins", "Not", "5"});
        command.onCommand(sender, null, "bffa", new String[]{"removecoins", "Not", "5"});
        command.onCommand(sender, null, "bffa", new String[]{"resetstats", "Not"});

        verify(data, never()).addCoins(any(), anyInt());
        verify(data, never()).removeCoins(any(), anyInt());
        verify(data, never()).resetStats(any());
        verify(sender, org.mockito.Mockito.atLeastOnce()).sendMessage(contains("Player not found"));
    }

    @Test
    void exactNameMatchesRegardlessOfCase() {
        command.onCommand(sender, null, "bffa", new String[]{"addcoins", "notch", "5"});
        command.onCommand(sender, null, "bffa", new String[]{"removecoins", "Notch", "2"});
        command.onCommand(sender, null, "bffa", new String[]{"resetstats", "NOTCH"});

        verify(data).addCoins(notchId, 5);
        verify(data).removeCoins(notchId, 2);
        verify(data).resetStats(notchId);
    }

    @Test
    void productionLookupIsExact() throws Exception {
        String source = Files.readString(Path.of("src/main/java/infinode/org/example/bFFA01/commands/BuildFFAAdminCommand.java"));
        assertTrue(source.contains("getPlayerExact"));
        assertFalse(source.contains("getPlayer("));
    }

    @Test
    void nonPositiveAmountIsRejected() {
        command.onCommand(sender, null, "bffa", new String[]{"addcoins", "Notch", "0"});
        verify(data, never()).addCoins(eq(notchId), anyInt());
    }
}
