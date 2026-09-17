package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * A YAML file inside the plugin folder. Wraps the load/save boilerplate and
 * reports failures through the plugin logger instead of a stack trace on
 * {@code System.err}.
 */
public final class YamlFile {

    private final Plugin plugin;
    private final String name;
    private final File file;
    private FileConfiguration config;

    /**
     * @param copyFromJar copy the bundled resource of the same name when the
     *                    file does not exist yet
     */
    public YamlFile(Plugin plugin, String name, boolean copyFromJar) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);

        if (!file.exists()) {
            if (copyFromJar && plugin.getResource(name) != null) {
                plugin.saveResource(name, false);
            } else {
                createEmpty();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        applyJarDefaults();
    }

    private void createEmpty() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin folder for " + name + ".");
            }
            if (!file.exists() && !file.createNewFile()) {
                plugin.getLogger().warning("Could not create " + name + ".");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create " + name, e);
        }
    }

    /**
     * Makes the bundled copy the default source, so keys added in a plugin
     * update resolve even when the admin's file predates them.
     */
    private void applyJarDefaults() {
        InputStream bundled = plugin.getResource(name);
        if (bundled == null) {
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
            config.options().copyDefaults(true);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled " + name, e);
        }
    }

    public FileConfiguration config() {
        return config;
    }

    public File file() {
        return file;
    }

    public String name() {
        return name;
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, e);
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        applyJarDefaults();
    }
}
