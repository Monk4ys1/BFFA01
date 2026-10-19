package infinode.org.example.bFFA01.ui;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Toggles, sound names, and which language keys drive the sidebar chrome.
 */
public final class PresentationSettings {

    private final boolean enabled;
    private final boolean tab;
    private final boolean actionBar;
    private final boolean bossBar;
    private final boolean titles;
    private final boolean chat;
    private final boolean firstJoin;
    private final int firstJoinCoins;
    private final int lowSeconds;
    private final String bossColor;
    private final String bossLowColor;
    private final List<String> effectCycle;
    private final Map<String, List<String>> particles;
    private final Map<String, List<String>> sounds;
    private final List<String> tabHeader;
    private final List<String> tabFooter;
    private final String actionBarKey;
    private final String bossBarKey;
    private final List<String> firstJoinLines;

    private PresentationSettings(
            boolean enabled,
            boolean tab,
            boolean actionBar,
            boolean bossBar,
            boolean titles,
            boolean chat,
            boolean firstJoin,
            int firstJoinCoins,
            int lowSeconds,
            String bossColor,
            String bossLowColor,
            List<String> effectCycle,
            Map<String, List<String>> particles,
            Map<String, List<String>> sounds,
            List<String> tabHeader,
            List<String> tabFooter,
            String actionBarKey,
            String bossBarKey,
            List<String> firstJoinLines
    ) {
        this.enabled = enabled;
        this.tab = tab;
        this.actionBar = actionBar;
        this.bossBar = bossBar;
        this.titles = titles;
        this.chat = chat;
        this.firstJoin = firstJoin;
        this.firstJoinCoins = firstJoinCoins;
        this.lowSeconds = lowSeconds;
        this.bossColor = bossColor;
        this.bossLowColor = bossLowColor;
        this.effectCycle = effectCycle;
        this.particles = particles;
        this.sounds = sounds;
        this.tabHeader = tabHeader;
        this.tabFooter = tabFooter;
        this.actionBarKey = actionBarKey;
        this.bossBarKey = bossBarKey;
        this.firstJoinLines = firstJoinLines;
    }

    public static PresentationSettings read(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml == null ? "" : yaml);
        } catch (Exception ex) {
            config = new YamlConfiguration();
        }
        List<String> cycle = ids(config.getStringList("effect-cycle"));
        if (cycle.isEmpty()) {
            cycle = List.of("none", "totem", "flame", "heart", "crit", "enchant", "note");
        }
        return new PresentationSettings(
                config.getBoolean("enabled", true),
                config.getBoolean("tab.enabled", true),
                config.getBoolean("actionbar.enabled", true),
                config.getBoolean("bossbar.enabled", true),
                config.getBoolean("titles.enabled", true),
                config.getBoolean("chat.enabled", true),
                config.getBoolean("first-join.enabled", true),
                Math.max(0, config.getInt("first-join.coins", 25)),
                Math.max(1, config.getInt("bossbar.low-seconds", 30)),
                token(config.getString("bossbar.color"), "BLUE"),
                token(config.getString("bossbar.low-color"), "RED"),
                cycle,
                groups(config, "effects"),
                groups(config, "sounds"),
                keys(config.getStringList("tab.header"), List.of("tab-header-1", "tab-header-2")),
                keys(config.getStringList("tab.footer"), List.of("tab-footer-1", "tab-footer-2")),
                key(config.getString("actionbar.idle"), "actionbar-idle"),
                key(config.getString("bossbar.title"), "bossbar-title"),
                keys(config.getStringList("first-join.lines"), List.of("first-join-1", "first-join-2", "first-join-3"))
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean tab() {
        return enabled && tab;
    }

    public boolean actionBar() {
        return enabled && actionBar;
    }

    public boolean bossBar() {
        return enabled && bossBar;
    }

    public boolean titles() {
        return enabled && titles;
    }

    public boolean chat() {
        return enabled && chat;
    }

    public boolean firstJoin() {
        return enabled && firstJoin;
    }

    public int firstJoinCoins() {
        return firstJoinCoins;
    }

    public int lowSeconds() {
        return lowSeconds;
    }

    public String bossColor() {
        return bossColor;
    }

    public String bossLowColor() {
        return bossLowColor;
    }

    public List<String> effectCycle() {
        return effectCycle;
    }

    public List<String> particles(String effect) {
        return particles.getOrDefault(effect, List.of());
    }

    public List<String> sounds(String cue) {
        return sounds.getOrDefault(cue, List.of());
    }

    public List<String> tabHeader() {
        return tabHeader;
    }

    public List<String> tabFooter() {
        return tabFooter;
    }

    public String actionBarKey() {
        return actionBarKey;
    }

    public String bossBarKey() {
        return bossBarKey;
    }

    public List<String> firstJoinLines() {
        return firstJoinLines;
    }

    public String nextEffect(String current) {
        if (effectCycle.isEmpty()) {
            return "none";
        }
        int index = effectCycle.indexOf(current);
        return effectCycle.get((index + 1) % effectCycle.size());
    }

    private static Map<String, List<String>> groups(YamlConfiguration config, String path) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        var section = config.getConfigurationSection(path);
        if (section == null) {
            return Map.of();
        }
        for (String key : section.getKeys(false)) {
            groups.put(key.toLowerCase(Locale.ROOT), names(section.getStringList(key)));
        }
        return Map.copyOf(groups);
    }

    private static List<String> ids(List<String> raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String name = entry.trim().toLowerCase(Locale.ROOT);
            if (name.matches("[a-z0-9_-]{1,24}")) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static List<String> names(List<String> raw) {
        List<String> names = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String name = entry.trim().toUpperCase(Locale.ROOT);
            if (name.matches("[A-Z0-9_]{1,64}")) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private static List<String> keys(List<String> raw, List<String> fallback) {
        List<String> keys = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                String key = key(entry, "");
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        }
        return keys.isEmpty() ? fallback : List.copyOf(keys);
    }

    private static String key(String raw, String fallback) {
        if (raw == null || !raw.trim().matches("[a-z0-9_.-]{1,48}")) {
            return fallback;
        }
        return raw.trim();
    }

    private static String token(String raw, String fallback) {
        if (raw == null || !raw.trim().matches("[A-Za-z0-9_]{1,32}")) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
