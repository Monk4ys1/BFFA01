package infinode.org.example.bFFA01.config;

import infinode.org.example.bFFA01.network.NetworkSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvOverlayTest {

    @Test
    void environmentReplacesStorageAndNetworkWithoutWritingAFile() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("storage.type", "yaml");
        config.set("network.enabled", false);
        EnvOverlay.apply(config, Map.of(
                "BFFA_STORAGE_TYPE", "mysql",
                "BFFA_MYSQL_HOST", "mariadb",
                "BFFA_MYSQL_PORT", "3306",
                "BFFA_MYSQL_DATABASE", "bffa",
                "BFFA_MYSQL_USERNAME", "bffa",
                "BFFA_MYSQL_PASSWORD", "secret",
                "BFFA_NETWORK", "auto",
                "BFFA_REDIS_HOST", "redis",
                "BFFA_NETWORK_SECRET", "cluster-secret",
                "BFFA_LANGUAGE", "de"
        ));
        assertEquals("mysql", config.getString("storage.type"));
        assertEquals("mariadb", config.getString("storage.mysql.host"));
        assertEquals(3306, config.getInt("storage.mysql.port"));
        assertEquals("secret", config.getString("storage.mysql.password"));
        assertEquals("de", config.getString("language"));
        assertEquals("auto", config.getString("network.enabled"));
        assertTrue(NetworkSettings.load(config).enabled());
        assertTrue(NetworkSettings.load(config).redis());
        assertEquals("cluster-secret", NetworkSettings.load(config).secret());
        assertTrue(NetworkSettings.load(config).signed());
    }

    @Test
    void autoStaysOffForYamlAndFalseStaysOffForMysql() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("storage.type", "yaml");
        yaml.set("network.enabled", "auto");
        assertFalse(NetworkSettings.load(yaml).enabled());

        YamlConfiguration mysql = new YamlConfiguration();
        mysql.set("storage.type", "mysql");
        mysql.set("network.enabled", false);
        assertFalse(NetworkSettings.load(mysql).enabled());

        mysql.set("network.enabled", true);
        mysql.set("storage.type", "yaml");
        assertTrue(NetworkSettings.load(mysql).enabled());
    }
}
