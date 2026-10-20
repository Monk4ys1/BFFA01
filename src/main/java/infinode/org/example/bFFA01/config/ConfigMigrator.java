package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * Moves an existing config.yml forward without dropping maps, kits, or messages.
 * The first migration copies the file to {@code config.yml.v1.bak} because Bukkit
 * rewrites the YAML and drops comments.
 */
public final class ConfigMigrator {

    public static final int CURRENT = 2;

    private ConfigMigrator() {
    }

    public static boolean migrate(File file, Logger logger) {
        if (file == null || !file.isFile()) {
            return false;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int version = config.getInt("config-version", 1);
        if (version >= CURRENT) {
            return false;
        }
        backup(file.toPath(), version, logger);
        if (!config.contains("language")) {
            config.set("language", "en");
        }
        if (!config.contains("storage.type")) {
            config.set("storage.type", "yaml");
        }
        if (!config.contains("storage.sqlite.file")) {
            config.set("storage.sqlite.file", "data.db");
        }
        if (!config.contains("storage.mysql.host")) {
            config.set("storage.mysql.host", "127.0.0.1");
        }
        if (!config.contains("storage.mysql.port")) {
            config.set("storage.mysql.port", 3306);
        }
        if (!config.contains("storage.mysql.database")) {
            config.set("storage.mysql.database", "bffa");
        }
        if (!config.contains("storage.mysql.username")) {
            config.set("storage.mysql.username", "bffa");
        }
        if (!config.contains("storage.mysql.password")) {
            config.set("storage.mysql.password", "");
        }
        if (!config.contains("storage.mysql.parameters")) {
            config.set("storage.mysql.parameters", "useSSL=false&allowPublicKeyRetrieval=true");
        }
        if (!config.contains("holograms.enabled")) {
            config.set("holograms.enabled", false);
        }
        config.set("config-version", CURRENT);
        try {
            config.save(file);
        } catch (IOException ex) {
            if (logger != null) {
                logger.warning("Could not migrate config.yml: " + ex.getMessage());
            }
            return false;
        }
        return true;
    }

    private static void backup(Path file, int version, Logger logger) {
        Path backup = file.resolveSibling("config.yml.v" + version + ".bak");
        if (Files.exists(backup)) {
            return;
        }
        try {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            if (logger != null) {
                logger.warning("Could not back up config.yml before migration: " + ex.getMessage());
            }
        }
    }
}
