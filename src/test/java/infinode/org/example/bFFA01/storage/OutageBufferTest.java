package infinode.org.example.bFFA01.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutageBufferTest {

    @TempDir
    Path tempDir;

    @Test
    void repeatedSpillsKeepOneIdAndAddTheDeltas() throws Exception {
        Path file = tempDir.resolve("mysql-buffer.yml");
        UUID id = UUID.randomUUID();
        OutageBuffer.merge(file, List.of(spill(id, 2, 4, false)));
        String bufferId = YamlConfiguration.loadConfiguration(file.toFile()).getString("buffer-id");
        OutageBuffer.merge(file, List.of(spill(id, 1, -3, false)));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

        assertEquals(bufferId, yaml.getString("buffer-id"));
        assertEquals(36, bufferId.length());
        assertEquals(3, yaml.getInt(id + ".kills"));
        assertEquals(1, yaml.getInt(id + ".coins"));
        assertEquals("Ada", yaml.getString(id + ".name"));
    }

    @Test
    void aResetReplacesCountersAlreadyQueued() throws Exception {
        Path file = tempDir.resolve("mysql-buffer.yml");
        UUID id = UUID.randomUUID();
        OutageBuffer.merge(file, List.of(spill(id, 5, 2, false)));
        OutageBuffer.merge(file, List.of(spill(id, 1, 0, true)));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

        assertTrue(yaml.getBoolean(id + ".reset"));
        assertEquals(1, yaml.getInt(id + ".kills"));
        assertEquals(2, yaml.getInt(id + ".coins"));
    }

    @Test
    void connectionRefusedIsAnOutageAndALeaseMissIsNot() {
        assertTrue(OutageBuffer.unavailable(new SQLException("Socket fail to connect to 127.0.0.1. Connection refused")));
        assertTrue(OutageBuffer.unavailable(new SQLException("timed out", "08001")));
        assertFalse(OutageBuffer.unavailable(new SQLException("row is leased", "23000")));
    }

    @Test
    void resetInsideTheBufferTransactionAppliesOnce() throws Exception {
        Path folder = tempDir.resolve("db");
        Files.createDirectories(folder);
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", "players.db");
        UUID id = UUID.randomUUID();
        String buffer = UUID.randomUUID().toString();
        try (JdbcPlayerRepository repository = JdbcPlayerRepository.open(
                infinode.org.example.bFFA01.config.StorageSettings.resolve(config), folder)) {
            repository.applyDelta(new CountDelta(id, 5, 1, 4, 0, 9L, 0L, 0L, 0L));
            CountDelta afterReset = new CountDelta(id, 1, 0, 0, 0, 2L, 0L, 0L, 0L);
            assertTrue(repository.applyBuffer(buffer, List.of(afterReset), List.of(), Set.of(id), Set.of()));
            assertFalse(repository.applyBuffer(buffer, List.of(afterReset), List.of(), Set.of(id), Set.of()));
            PlayerRecord stored = repository.loadOne(id);
            assertEquals(1, stored.kills());
            assertEquals(0, stored.deaths());
            assertEquals(4, stored.coins());
            assertEquals(2L, stored.damageDealt());
        }
    }

    private static OutageBuffer.Spill spill(UUID id, int kills, int coins, boolean reset) {
        PlayerRecord profile = new PlayerRecord(id);
        profile.name("Ada");
        return new OutageBuffer.Spill(new CountDelta(id, kills, 0, coins, 0, 0L, 0L, 0L, 0L), reset, false, profile);
    }
}
