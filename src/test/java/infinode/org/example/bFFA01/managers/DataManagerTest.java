package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void saveDelayBatchesASecondOfWrites() {
        assertEquals(20L, DataManager.SAVE_DELAY_TICKS);
    }

    @Test
    void loadErrorNeverOverwritesTheFile() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        Path dataFile = dataFolder.resolve("data.yml");
        byte[] original = "not: [valid\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dataFile, original);

        BFFA01 plugin = plugin(dataFolder);
        DataManager data = new DataManager(plugin);
        UUID player = UUID.randomUUID();

        assertFalse(data.isWritable());
        data.addKill(player);
        data.addDeath(player);
        data.addCoins(player, 5);
        data.flush();

        assertArrayEquals(original, Files.readAllBytes(dataFile));
        assertEquals(0, data.getKills(player));
    }

    @Test
    void missingFileIsCreatedWithoutAScheduler() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);

        DataManager data = new DataManager(plugin(dataFolder));

        assertTrue(data.isWritable());
        assertTrue(Files.isRegularFile(dataFolder.resolve("data.yml")));
        data.flush();
    }

    private static BFFA01 plugin(Path dataFolder) {
        BFFA01 plugin = mock(BFFA01.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("bffa-test"));
        return plugin;
    }
}
