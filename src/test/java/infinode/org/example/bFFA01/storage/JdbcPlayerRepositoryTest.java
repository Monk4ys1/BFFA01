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
            record.addDealt(250);
            record.addTaken(80);
            record.addAssist();
            record.streak(4);
            repository.saveAll(List.of(record));
            PlayerRecord again = repository.loadOne(id);
            assertEquals(250, again.damageDealt());
            assertEquals(80, again.damageTaken());
            assertEquals(1, again.assists());
            assertEquals(4, again.streak());
        }
    }

    @Test
    void legacySqliteTableGainsDamageColumns() throws Exception {
        Path file = tempDir.resolve("old.db");
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            connection.createStatement().execute(
                    "CREATE TABLE bffa_player ("
                            + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                            + "player_name VARCHAR(16) NOT NULL DEFAULT '',"
                            + "kills INT NOT NULL DEFAULT 0,"
                            + "deaths INT NOT NULL DEFAULT 0,"
                            + "coins INT NOT NULL DEFAULT 0,"
                            + "upgrades VARCHAR(512) NOT NULL DEFAULT '',"
                            + "layout VARCHAR(320) NOT NULL DEFAULT '')");
            connection.createStatement().execute(
                    "INSERT INTO bffa_player (id, player_name, kills) VALUES ('11111111-1111-1111-1111-111111111111', 'Old', 4)");
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", "old.db");
        try (JdbcPlayerRepository repository = JdbcPlayerRepository.open(StorageSettings.resolve(config), tempDir)) {
            List<PlayerRecord> loaded = repository.loadAll();
            assertEquals(1, loaded.size());
            assertEquals(4, loaded.get(0).kills());
            assertEquals(0, loaded.get(0).damageDealt());
            assertEquals(0, loaded.get(0).assists());
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

    @Test
    void twoConnectionsAddKillsInsteadOfOverwriting() throws Exception {
        StorageSettings settings = sqlite("shared.db");
        UUID id = UUID.randomUUID();
        try (JdbcPlayerRepository first = JdbcPlayerRepository.open(settings, tempDir);
             JdbcPlayerRepository second = JdbcPlayerRepository.open(settings, tempDir)) {
            assertEquals("alpha-one", first.claimInstance("alpha-one"));
            String other = second.claimInstance("alpha-one");
            assertTrue(!other.equals("alpha-one"));
            first.applyDelta(new CountDelta(id, 1, 0, 4, 0, 100L, 0L, 100L, 0L));
            second.applyDelta(new CountDelta(id, 1, 1, 3, 1, 50L, 20L, 50L, 20L));
            PlayerRecord stored = first.loadOne(id);
            assertEquals(2, stored.kills());
            assertEquals(1, stored.deaths());
            assertEquals(7, stored.coins());
            assertEquals(1, stored.assists());
            assertEquals(150L, stored.damageDealt());
            assertEquals(20L, stored.damageTaken());
        }
    }

    @Test
    void aLiveLeaseBlocksProfileOverwriteUntilTheHeartbeatIsStale() throws Exception {
        StorageSettings settings = sqlite("lease.db");
        UUID id = UUID.randomUUID();
        try (JdbcPlayerRepository first = JdbcPlayerRepository.open(settings, tempDir);
             JdbcPlayerRepository second = JdbcPlayerRepository.open(settings, tempDir)) {
            first.claimInstance("alpha");
            second.claimInstance("beta");
            first.heartbeat(1);
            PlayerRecord owned = new PlayerRecord(id);
            owned.name("Alpha");
            owned.addKill();
            owned.layout(0, "STONE");
            first.saveAll(List.of(owned));

            PlayerRecord incoming = new PlayerRecord(id);
            incoming.name("Beta");
            incoming.layout(0, "DIRT");
            assertTrue(!second.saveProfile(incoming));
            assertEquals("Alpha", first.loadOne(id).name());
            assertEquals("STONE", first.loadOne(id).layout(0));

            try (java.sql.Connection raw = java.sql.DriverManager.getConnection(
                    "jdbc:sqlite:" + tempDir.resolve("lease.db").toAbsolutePath())) {
                raw.createStatement().execute("UPDATE bffa_instance SET beat=0 WHERE id='alpha'");
            }
            assertTrue(second.saveProfile(incoming));
            assertEquals("Beta", second.loadOne(id).name());
            assertEquals("DIRT", second.loadOne(id).layout(0));
            assertEquals(1, second.loadOne(id).kills());
        }
    }

    @Test
    void openingTheSameDatabaseTwiceKeepsTheMigratedTable() throws Exception {
        StorageSettings settings = sqlite("twice.db");
        try (JdbcPlayerRepository first = JdbcPlayerRepository.open(settings, tempDir)) {
            first.claimInstance("once");
            first.retire();
        }
        try (JdbcPlayerRepository second = JdbcPlayerRepository.open(settings, tempDir)) {
            assertEquals("once", second.claimInstance("once"));
            assertEquals(0, second.loadAll().size());
        }
    }

    private StorageSettings sqlite(String file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "sqlite");
        config.set("storage.sqlite.file", file);
        return StorageSettings.resolve(config);
    }
}
