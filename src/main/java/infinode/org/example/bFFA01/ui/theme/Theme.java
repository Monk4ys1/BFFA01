package infinode.org.example.bFFA01.ui.theme;

import infinode.org.example.bFFA01.text.ThemeText;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Palette and icons from theme.yml. Language files refer to them as {@code %color_primary%} and {@code %icon_map%}.
 */
public final class Theme {

    private final Map<String, String> colors;
    private final Map<String, String> icons;

    private Theme(Map<String, String> colors, Map<String, String> icons) {
        this.colors = colors;
        this.icons = icons;
    }

    public static Theme defaults() {
        return new Theme(ThemeText.defaultColors(), ThemeText.defaultIcons());
    }

    public static Theme load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml == null ? "" : yaml);
        } catch (Exception ex) {
            return defaults();
        }
        Map<String, String> colors = new LinkedHashMap<>();
        ConfigurationSection palette = config.getConfigurationSection("palette");
        if (palette != null) {
            for (String key : palette.getKeys(false)) {
                colors.put(key, palette.getString(key));
            }
        }
        Map<String, String> icons = new LinkedHashMap<>();
        ConfigurationSection marks = config.getConfigurationSection("icons");
        if (marks != null) {
            for (String key : marks.getKeys(false)) {
                icons.put(key, marks.getString(key));
            }
        }
        return new Theme(ThemeText.colors(colors), ThemeText.icons(icons));
    }

    public Map<String, String> colors() {
        return colors;
    }

    public Map<String, String> icons() {
        return icons;
    }

    public String color(String name) {
        return colors.getOrDefault(name, ThemeText.defaultColors().getOrDefault(name, "#ffffff"));
    }

    public String icon(String name) {
        return icons.getOrDefault(name, "");
    }
}
