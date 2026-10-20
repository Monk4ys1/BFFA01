package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.storage.KillEntry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        assertEquals(1, corruptCopies(dataFolder).size());
        assertArrayEquals(original, Files.readAllBytes(corruptCopies(dataFolder).get(0)));
        assertFalse(Files.exists(dataFolder.resolve("data.yml.corrupt")));

        new DataManager(plugin);
        List<Path> copies = corruptCopies(dataFolder);
        assertEquals(2, copies.size());
        for (Path copy : copies) {
            assertArrayEquals(original, Files.readAllBytes(copy));
        }
        assertEquals(0, data.getKills(player));
    }

    @Test
    void missingFileDoesNotCreateACorruptCopy() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);

        new DataManager(plugin(dataFolder));

        assertTrue(corruptCopies(dataFolder).isEmpty());
    }

    @Test
    void failedSavesBackOffAndThenCap() {
        assertEquals(20L, DataManager.backoffTicks(1));
        assertEquals(40L, DataManager.backoffTicks(2));
        assertEquals(80L, DataManager.backoffTicks(3));
        assertEquals(160L, DataManager.backoffTicks(4));
        assertEquals(320L, DataManager.backoffTicks(5));
        assertEquals(640L, DataManager.backoffTicks(6));
        assertEquals(640L, DataManager.backoffTicks(7));
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

    @Test
    void reloadKeepsYamlStats() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        BFFA01 plugin = plugin(dataFolder);
        DataManager data = new DataManager(plugin);
        UUID player = UUID.randomUUID();
        data.addKill(player);

        assertTrue(data.reconnect());
        assertEquals("yaml", data.storageLabel());
        assertEquals(1, data.getKills(player));
    }

    @Test
    void reloadReopensSqlite() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", "players.db");
        BFFA01 plugin = plugin(dataFolder);
        when(plugin.getConfig()).thenReturn(config);

        DataManager data = new DataManager(plugin);
        UUID player = UUID.randomUUID();
        data.addKill(player);
        data.addKill(player);

        assertTrue(data.reconnect());
        assertEquals("sqlite", data.storageLabel());
        assertEquals(2, data.getKills(player));
        data.flush();
    }

    @Test
    void leaderboardIsCachedUntilTheOrderChanges() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        DataManager data = new DataManager(plugin(dataFolder));
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        data.rememberName(ann, "Ann");
        data.rememberName(bob, "Bob");
        data.addKill(ann);
        data.addKill(bob);
        List<KillEntry> first = data.topKills(Integer.MAX_VALUE);
        assertEquals(ann, first.get(0).id());
        assertSame(first, data.topKills(Integer.MAX_VALUE));
        assertEquals(ann, data.topKills(1).get(0).id());

        data.addKill(bob);
        data.addKill(bob);
        List<KillEntry> next = data.topKills(2);
        assertEquals(bob, next.get(0).id());
        assertEquals("Ann", next.get(1).name());
    }

    @Test
    void mysqlBufferKeepsOneId() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        Path buffer = dataFolder.resolve("mysql-buffer.yml");
        BFFA01 plugin = plugin(dataFolder);

        new YamlPlayerData(plugin, buffer);
        String first = YamlConfiguration.loadConfiguration(buffer.toFile()).getString("buffer-id");
        new YamlPlayerData(plugin, buffer);

        assertNotNull(first);
        assertEquals(36, first.length());
        assertEquals(first, YamlConfiguration.loadConfiguration(buffer.toFile()).getString("buffer-id"));
    }

    @Test
    void sqliteAgreesOnOneClusterSecret() throws IOException {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", "players.db");
        BFFA01 plugin = plugin(dataFolder);
        when(plugin.getConfig()).thenReturn(config);

        DataManager data = new DataManager(plugin);
        String secret = data.sharedSecret();

        assertEquals(64, secret.length());
        assertEquals(secret, data.sharedSecret());
        data.flush();
    }

    private static List<Path> corruptCopies(Path dataFolder) throws IOException {
        List<Path> copies = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dataFolder, "data.yml.*.corrupt")) {
            for (Path path : stream) {
                copies.add(path);
            }
        }
        return copies;
    }

    private static BFFA01 plugin(Path dataFolder) {
        BFFA01 plugin = mock(BFFA01.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("bffa-test"));
        return plugin;
    }
}
