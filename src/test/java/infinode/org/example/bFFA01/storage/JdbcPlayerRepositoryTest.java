package infinode.org.example.bFFA01.storage;

import infinode.org.example.bFFA01.config.StorageSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcPlayerRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteRoundTripUpdatesTheSameRow() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", "players.db");
        StorageSettings settings = StorageSettings.resolve(config);

        UUID id = UUID.randomUUID();
        try (JdbcPlayerRepository repository = JdbcPlayerRepository.open(settings, tempDir)) {
            PlayerRecord record = new PlayerRecord(id);
            record.name("Notch");
            record.addKill();
            record.addKill();
            record.addDeath();
            record.addCoins(15);
            record.addUpgrade("sharpness");
            record.layout(0, "IRON_SWORD");
            repository.saveAll(List.of(record));

            record.addKill();
            repository.saveAll(List.of(record));

            List<PlayerRecord> loaded = repository.loadAll();
            assertEquals(1, loaded.size());
            PlayerRecord stored = loaded.get(0);
            assertEquals("Notch", stored.name());
            assertEquals(3, stored.kills());
            assertEquals(1, stored.deaths());
            assertEquals(15, stored.coins());
            assertTrue(stored.hasUpgrade("sharpness"));
            assertEquals("IRON_SWORD", stored.layout(0));
        }
    }

    @Test
    void invalidMysqlFallsBackToYaml() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "mysql");
        config.set("storage.mysql.host", "bad host");
        config.set("storage.mysql.database", "bffa");
        assertEquals(StorageSettings.Type.YAML, StorageSettings.resolve(config).type());
    }

    @Test
    void mysqlParametersDropLocalFileLoading() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "mysql");
        config.set("storage.mysql.host", "127.0.0.1");
        config.set("storage.mysql.database", "bffa");
        config.set("storage.mysql.parameters", "useSSL=false&allowLoadLocalInfile=true&statementInterceptors=evil");
        assertEquals("useSSL=false", StorageSettings.resolve(config).mysqlParameters());
    }
}
