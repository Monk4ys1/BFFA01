package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Brings a 4.x configuration up to the 5.x layout without losing anything the
 * admin configured.
 *
 * <p>4.x kept arena spawns and flat tuning keys inside {@code config.yml};
 * 5.x groups the tuning keys into sections and moves arenas into
 * {@code maps.yml}, which is what the documentation always claimed.
 */
public final class ConfigMigrator {

    /** Layout version written by this plugin build. */
    public static final int CURRENT_VERSION = 2;

    private ConfigMigrator() {
    }

    /**
     * @return {@code true} when something was migrated
     */
    public static boolean run(Plugin plugin, FileConfiguration config, Runnable saveConfig, YamlFile mapsFile) {
        if (config.getInt("config-version", 1) >= CURRENT_VERSION && !config.contains("maps", true)) {
            return false;
        }

        int movedMaps = moveMaps(config, mapsFile);

        move(config, "map-swap-interval", "arena.map-swap-interval");
        move(config, "block-remove-delay", "building.block-remove-delay");
        move(config, "combat-regen-pause", "combat.tag-seconds");
        move(config, "default-death-y-level", "arena.default-death-y-level");
        move(config, "default-safezone-y-level", "arena.default-safezone-y-level");
        // 4.x only had one usage message; 5.x prints per-subcommand usage.
        config.set("messages.command-usage", null);

        config.set("config-version", CURRENT_VERSION);
        saveConfig.run();
        if (movedMaps > 0) {
            mapsFile.save();
        }

        plugin.getLogger().info("Migrated the configuration to version " + CURRENT_VERSION
                + (movedMaps > 0 ? " and moved " + movedMaps + " arena(s) into maps.yml." : "."));
        return true;
    }

    private static int moveMaps(FileConfiguration config, YamlFile mapsFile) {
        if (!config.contains("maps", true)) {
            return 0;
        }
        ConfigurationSection source = config.getConfigurationSection("maps");
        if (source == null) {
            config.set("maps", null);
            return 0;
        }
        FileConfiguration target = mapsFile.config();
        int moved = 0;
        for (String mapName : source.getKeys(false)) {
            ConfigurationSection map = source.getConfigurationSection(mapName);
            if (map == null) {
                continue;
            }
            // Never overwrite an arena that already exists in maps.yml.
            if (target.contains("maps." + mapName, true)) {
                continue;
            }
            for (String key : map.getKeys(true)) {
                if (!map.isConfigurationSection(key)) {
                    target.set("maps." + mapName + "." + key, map.get(key));
                }
            }
            moved++;
        }
        config.set("maps", null);
        return moved;
    }

    private static void move(FileConfiguration config, String oldPath, String newPath) {
        if (!config.contains(oldPath, true)) {
            return;
        }
        if (!config.contains(newPath, true)) {
            config.set(newPath, config.get(oldPath));
        }
        config.set(oldPath, null);
    }
}
