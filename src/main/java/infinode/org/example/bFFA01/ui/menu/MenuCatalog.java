package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chest menus described in menus.yml. Slots stay inside a 6-row chest so Bedrock clients can use them.
 */
public final class MenuCatalog {

    private static final Pattern ACTION = Pattern.compile(
            "none|back|close|refresh|page0|page:next|page:prev|open:[a-z0-9_-]{1,24}(?::[a-z0-9_-]{1,24})?|"
                    + "toggle:scoreboard|toggle:bossbar|toggle:actionbar|cycle:effect|kit:save|kit:reset");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,48}");
    private static final Pattern MATERIAL = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");

    private final String filler;
    private final List<String> frame;
    private final Map<String, Menu> menus;
    private final List<Category> categories;
    private final List<String> issues;

    private MenuCatalog(String filler, List<String> frame, Map<String, Menu> menus, List<Category> categories, List<String> issues) {
        this.filler = filler;
        this.frame = frame;
        this.menus = menus;
        this.categories = categories;
        this.issues = issues;
    }

    public static MenuCatalog read(String yaml) {
        List<String> issues = new ArrayList<>();
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml == null ? "" : yaml);
        } catch (Exception ex) {
            issues.add("unreadable menus.yml");
            return new MenuCatalog("GRAY_STAINED_GLASS_PANE", List.of(), Map.of(), List.of(), List.copyOf(issues));
        }
        String filler = material(config.getString("filler"), "GRAY_STAINED_GLASS_PANE", "filler", issues);
        List<String> frame = new ArrayList<>();
        for (String entry : config.getStringList("frame")) {
            frame.add(material(entry, filler, "frame", issues));
        }
        if (frame.isEmpty()) {
            frame.add("CYAN_STAINED_GLASS_PANE");
        }
        Map<String, Menu> menus = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("menus");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (!ID.matcher(id).matches()) {
                    issues.add("menu id " + id);
                    continue;
                }
                ConfigurationSection menu = section.getConfigurationSection(id);
                if (menu != null) {
                    menus.put(id, menu(id, menu, issues));
                }
            }
        }
        List<Category> categories = categories(config.getConfigurationSection("shop-categories"), menus.get("shop"), issues);
        return new MenuCatalog(filler, List.copyOf(frame), Map.copyOf(menus), List.copyOf(categories), List.copyOf(issues));
    }

    public String filler() {
        return filler;
    }

    public List<String> frame() {
        return frame;
    }

    public Menu menu(String id) {
        return menus.get(id);
    }

    public Map<String, Menu> menus() {
        return menus;
    }

    public List<Category> categories() {
        return categories;
    }

    public List<String> issues() {
        return issues;
    }

    private static Menu menu(String id, ConfigurationSection section, List<String> issues) {
        int rows = section.getInt("rows", 6);
        if (rows < 1 || rows > 6) {
            issues.add(id + " rows");
            rows = Math.max(1, Math.min(6, rows));
        }
        int size = rows * 9;
        String title = key(section.getString("title"), id + " title", issues);
        List<Integer> content = slots(section.getIntegerList("content-slots"), size, id + " content", issues);
        List<Integer> layout = slots(section.getIntegerList("layout-slots"), size, id + " layout", issues);
        if (!layout.isEmpty() && layout.size() != 9) {
            issues.add(id + " layout-slots");
        }
        List<Button> buttons = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String buttonId : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(buttonId);
                if (item == null) {
                    continue;
                }
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= size) {
                    issues.add(id + " " + buttonId + " slot");
                    continue;
                }
                if (used.contains(slot) || content.contains(slot) || layout.contains(slot)) {
                    issues.add(id + " " + buttonId + " overlap");
                }
                used.add(slot);
                String action = item.getString("action", "none");
                if (action == null || !ACTION.matcher(action).matches()) {
                    issues.add(id + " " + buttonId + " action");
                    action = "none";
                }
                List<String> lore = new ArrayList<>();
                for (String loreKey : item.getStringList("lore")) {
                    lore.add(key(loreKey, id + " " + buttonId + " lore", issues));
                }
                buttons.add(new Button(
                        buttonId,
                        slot,
                        material(item.getString("material"), "PAPER", id + " " + buttonId, issues),
                        key(item.getString("name"), id + " " + buttonId + " name", issues),
                        List.copyOf(lore),
                        action
                ));
            }
        }
        return new Menu(id, rows, title, content, layout, List.copyOf(buttons));
    }

    private static List<Category> categories(ConfigurationSection section, Menu shop, List<String> issues) {
        if (section == null) {
            return List.of();
        }
        int size = shop == null ? 54 : shop.rows() * 9;
        List<Integer> blocked = new ArrayList<>();
        if (shop != null) {
            blocked.addAll(shop.contentSlots());
            for (Button button : shop.buttons()) {
                blocked.add(button.slot());
            }
        }
        List<Category> categories = new ArrayList<>();
        List<Integer> used = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            if (!ID.matcher(id).matches()) {
                issues.add("category " + id);
                continue;
            }
            ConfigurationSection category = section.getConfigurationSection(id);
            if (category == null) {
                continue;
            }
            int slot = category.getInt("slot", -1);
            if (slot < 0 || slot >= size || blocked.contains(slot) || used.contains(slot)) {
                issues.add("category " + id + " slot");
            }
            used.add(slot);
            List<String> offers = new ArrayList<>();
            for (String offer : category.getStringList("offers")) {
                if (offer == null || !ID.matcher(offer).matches()) {
                    issues.add("category " + id + " offer");
                    continue;
                }
                offers.add(offer);
            }
            if (offers.isEmpty()) {
                issues.add("category " + id + " offers");
            }
            List<String> lore = new ArrayList<>();
            for (String loreKey : category.getStringList("lore")) {
                lore.add(key(loreKey, "category " + id + " lore", issues));
            }
            categories.add(new Category(
                    id,
                    slot,
                    material(category.getString("material"), "CHEST", "category " + id, issues),
                    key(category.getString("name"), "category " + id + " name", issues),
                    List.copyOf(lore),
                    List.copyOf(offers)
            ));
        }
        return categories;
    }

    private static List<Integer> slots(List<Integer> raw, int size, String label, List<String> issues) {
        List<Integer> slots = new ArrayList<>();
        if (raw == null) {
            return List.of();
        }
        for (Integer slot : raw) {
            if (slot == null || slot < 0 || slot >= size || slots.contains(slot)) {
                issues.add(label);
                continue;
            }
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private static String material(String raw, String fallback, String label, List<String> issues) {
        if (raw == null) {
            issues.add(label + " material");
            return fallback;
        }
        String name = raw.trim().toUpperCase(Locale.ROOT);
        if (!MATERIAL.matcher(name).matches()) {
            issues.add(label + " material");
            return fallback;
        }
        return name;
    }

    private static String key(String raw, String label, List<String> issues) {
        if (raw == null || !KEY.matcher(raw.trim()).matches()) {
            issues.add(label);
            return "menu-missing";
        }
        return raw.trim();
    }

    public record Menu(String id, int rows, String titleKey, List<Integer> contentSlots, List<Integer> layoutSlots, List<Button> buttons) {
        public Menu {
            contentSlots = contentSlots == null ? List.of() : List.copyOf(contentSlots);
            layoutSlots = layoutSlots == null ? List.of() : List.copyOf(layoutSlots);
            buttons = buttons == null ? List.of() : List.copyOf(buttons);
        }

        public int size() {
            return rows * 9;
        }
    }

    public record Button(String id, int slot, String material, String nameKey, List<String> loreKeys, String action) {
        public Button {
            loreKeys = loreKeys == null ? List.of() : List.copyOf(loreKeys);
        }
    }

    public record Category(String id, int slot, String material, String nameKey, List<String> loreKeys, List<String> offers) {
        public Category {
            loreKeys = loreKeys == null ? List.of() : List.copyOf(loreKeys);
            offers = offers == null ? List.of() : List.copyOf(offers);
        }
    }

}
