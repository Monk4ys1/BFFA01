package infinode.org.example.bFFA01.text;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Flat message catalog. Files live in {@code lang/<language>.yml}.
 * A {@code messages:} section still present in config.yml overrides the same keys.
 */
public final class Messages {

    private final Map<String, String> values;
    private volatile Map<String, String> colors = ThemeText.defaultColors();
    private volatile Map<String, String> icons = ThemeText.defaultIcons();

    private Messages(Map<String, String> values) {
        this.values = values;
    }

    public void style(Map<String, String> colors, Map<String, String> icons) {
        this.colors = colors == null || colors.isEmpty() ? ThemeText.defaultColors() : Map.copyOf(colors);
        this.icons = icons == null || icons.isEmpty() ? ThemeText.defaultIcons() : Map.copyOf(icons);
    }

    public String theme(String template) {
        return ThemeText.apply(template, colors, icons);
    }

    public static Messages load(Path dataFolder, String language, FileConfiguration config) {
        return load(dataFolder, language, overrides(config), Messages.class.getClassLoader());
    }

    static Messages load(Path dataFolder, String language, Map<String, String> overrides, ClassLoader resources) {
        String selected = normalize(language);
        Map<String, String> values = new HashMap<>();
        readResource(resources, "lang/en.yml", values);
        overlayFile(dataFolder.resolve("lang").resolve("en.yml"), values);
        if (!"en".equals(selected)) {
            readResource(resources, "lang/" + selected + ".yml", values);
            overlayFile(dataFolder.resolve("lang").resolve(selected + ".yml"), values);
        }
        if (overrides != null) {
            values.putAll(overrides);
        }
        return new Messages(Collections.unmodifiableMap(values));
    }

    public String raw(String key) {
        return values.getOrDefault(key, key);
    }

    public String render(String key, Map<String, String> placeholders) {
        return MiniText.render(theme(raw(key)), placeholders);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    private static String normalize(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }
        String value = language.toLowerCase(Locale.ROOT).trim();
        if (!value.matches("[a-z]{2}")) {
            return "en";
        }
        return value;
    }

    private static Map<String, String> overrides(FileConfiguration config) {
        if (config == null) {
            return Map.of();
        }
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section == null) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (String key : section.getKeys(false)) {
            if (section.isString(key)) {
                map.put(key, section.getString(key));
            }
        }
        return map;
    }

    private static void readResource(ClassLoader resources, String path, Map<String, String> into) {
        if (resources == null) {
            return;
        }
        try (InputStream in = resources.getResourceAsStream(path)) {
            if (in == null) {
                return;
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            into.putAll(readFlat(text));
        } catch (IOException ignored) {
            // Keep whatever was already loaded.
        }
    }

    private static void overlayFile(Path file, Map<String, String> into) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            into.putAll(readFlat(Files.readString(file)));
        } catch (IOException ignored) {
            // A broken language file must not hide the embedded defaults.
        }
    }

    static Map<String, String> readFlat(String yaml) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(yaml == null ? "" : yaml);
        } catch (Exception ex) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String key : configuration.getKeys(false)) {
            if (configuration.isString(key)) {
                values.put(key, configuration.getString(key));
            }
        }
        return values;
    }
}
