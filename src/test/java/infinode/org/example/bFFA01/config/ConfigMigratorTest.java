package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMigratorTest {

    @TempDir
    Path tempDir;

    @Test
    void versionOneGainsStorageAndKeepsMaps() throws Exception {
        File file = tempDir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), "maps:\n  arena:\n    world: world\nmessages:\n  join: hello\n");

        assertTrue(ConfigMigrator.migrate(file, Logger.getLogger("bffa-test")));

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        assertEquals(ConfigMigrator.CURRENT, config.getInt("config-version"));
        assertEquals("en", config.getString("language"));
        assertEquals("yaml", config.getString("storage.type"));
        assertEquals(false, config.getBoolean("holograms.enabled"));
        assertEquals("world", config.getString("maps.arena.world"));
        assertEquals("hello", config.getString("messages.join"));
        assertTrue(Files.isRegularFile(tempDir.resolve("config.yml.v1.bak")));
    }

    @Test
    void currentConfigIsLeftAlone() throws Exception {
        File file = tempDir.resolve("config.yml").toFile();
        Files.writeString(file.toPath(), "config-version: " + ConfigMigrator.CURRENT + "\n");
        assertFalse(ConfigMigrator.migrate(file, Logger.getLogger("bffa-test")));
        assertFalse(Files.exists(tempDir.resolve("config.yml.v" + ConfigMigrator.CURRENT + ".bak")));
    }
}
