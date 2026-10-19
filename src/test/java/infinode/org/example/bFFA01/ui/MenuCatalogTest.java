package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.ui.menu.MenuCatalog;
import infinode.org.example.bFFA01.ui.prefs.PlayerPreferences;
import infinode.org.example.bFFA01.ui.theme.Theme;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuCatalogTest {

    private static final List<String> CODE_KEYS = List.of(
            "menu-missing", "menu-kit-empty", "menu-kit-selected", "kit-slot", "menu-maps-empty",
            "menu-maps-current", "menu-maps-votes", "menu-maps-click", "menu-maps-voted", "menu-map-name",
            "menu-stats-empty", "menu-stats-rank", "menu-stats-rank-lore", "menu-state-on", "menu-state-off",
            "menu-effect-none", "menu-effect-totem", "menu-effect-flame", "menu-effect-heart", "menu-effect-crit",
            "menu-effect-enchant", "menu-effect-note", "vote-cast", "vote-changed", "tab-header-1", "tab-header-2",
            "tab-footer-1", "tab-footer-2", "actionbar-idle", "bossbar-title", "title-kill", "title-kill-sub",
            "title-death", "title-death-sub", "title-streak", "title-streak-sub", "title-map", "title-map-sub",
            "title-map-soon", "title-map-soon-sub", "title-first-join", "title-first-join-sub", "first-join-1",
            "first-join-2", "first-join-3", "first-join-coins", "chat-format"
    );

    @TempDir
    Path tempDir;

    @Test
    void shippedMenusFitAChestAndUseTranslatedKeys() throws Exception {
        MenuCatalog catalog = MenuCatalog.read(resource("menus.yml"));
        assertEquals(List.of(), catalog.issues());
        assertEquals(6, catalog.menus().size());
        assertEquals(9, catalog.menu("kit").layoutSlots().size());
        Set<String> offers = new HashSet<>();
        for (MenuCatalog.Category category : catalog.categories()) {
            assertTrue(category.slot() >= 0 && category.slot() < catalog.menu("shop").size());
            offers.addAll(category.offers());
        }
        for (ShopOffer offer : ShopOffer.values()) {
            assertTrue(offers.contains(offer.id()), offer.id());
        }
        assertEquals(ShopOffer.values().length, offers.size());

        YamlConfiguration english = yaml("lang/en.yml");
        YamlConfiguration german = yaml("lang/de.yml");
        for (String key : keys(catalog)) {
            assertTrue(english.isString(key), key);
            assertTrue(german.isString(key), key);
        }
        PresentationSettings settings = PresentationSettings.read(resource("presentation.yml"));
        assertTrue(settings.tab());
        assertTrue(settings.effectCycle().contains("none"));
        assertFalse(settings.sounds("kill").isEmpty());
        assertEquals("TOTEM_OF_UNDYING", settings.particles("totem").get(0));
        Theme theme = Theme.load(resource("theme.yml"));
        assertEquals("#7ad7ff", theme.color("primary"));
        assertFalse(theme.icon("streak").isBlank());

        Path preview = Path.of("target", "menu-previews");
        Files.createDirectories(preview);
        for (MenuCatalog.Menu menu : catalog.menus().values()) {
            ImageIO.write(draw(menu, catalog), "png", preview.resolve(menu.id() + ".png").toFile());
        }
        ImageIO.write(palette(theme), "png", preview.resolve("palette.png").toFile());
    }

    @Test
    void preferencesRoundTripAndRefuseABrokenFile() throws Exception {
        PlayerPreferences preferences = new PlayerPreferences(tempDir.resolve("preferences.yml").toFile());
        UUID id = UUID.randomUUID();
        preferences.effect(id, "flame");
        preferences.scoreboard(id, false);
        preferences.welcomed(id, true);
        PlayerPreferences again = new PlayerPreferences(tempDir.resolve("preferences.yml").toFile());
        again.load();
        assertEquals("flame", again.effect(id));
        assertFalse(again.scoreboard(id));
        assertTrue(again.welcomed(id));

        Files.writeString(tempDir.resolve("broken.yml"), "players: [\n");
        PlayerPreferences broken = new PlayerPreferences(tempDir.resolve("broken.yml").toFile());
        broken.load();
        assertFalse(broken.writable());
    }

    private static List<String> keys(MenuCatalog catalog) {
        List<String> keys = new ArrayList<>(CODE_KEYS);
        for (MenuCatalog.Menu menu : catalog.menus().values()) {
            keys.add(menu.titleKey());
            for (MenuCatalog.Button button : menu.buttons()) {
                keys.add(button.nameKey());
                keys.addAll(button.loreKeys());
            }
        }
        for (MenuCatalog.Category category : catalog.categories()) {
            keys.add(category.nameKey());
            keys.addAll(category.loreKeys());
        }
        return keys;
    }

    private static BufferedImage draw(MenuCatalog.Menu menu, MenuCatalog catalog) {
        int cell = 58;
        int gap = 6;
        int pad = 28;
        int header = 56;
        int width = pad * 2 + 9 * cell + 8 * gap;
        int height = pad + header + menu.rows() * cell + (menu.rows() - 1) * gap + pad;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(0x12151C));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(0x7AD7FF));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        graphics.drawString(menu.id(), pad, pad + 24);
        for (int slot = 0; slot < menu.size(); slot++) {
            int col = slot % 9;
            int row = slot / 9;
            int x = pad + col * (cell + gap);
            int y = pad + header + row * (cell + gap);
            String material = materialAt(menu, catalog, slot);
            Color fill = new Color(color(material));
            graphics.setColor(fill);
            graphics.fillRoundRect(x, y, cell, cell, 14, 14);
            int luminance = (fill.getRed() * 299 + fill.getGreen() * 587 + fill.getBlue() * 114) / 1000;
            graphics.setColor(luminance > 160 ? new Color(0x12151C) : Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            String label = labelAt(menu, catalog, slot);
            graphics.drawString(trim(label, 8), x + 6, y + 32);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage palette(Theme theme) {
        String[] names = {"primary", "secondary", "accent", "success", "danger", "muted", "soft", "warm"};
        BufferedImage image = new BufferedImage(760, 140, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x12151C));
        graphics.fillRect(0, 0, 760, 140);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        for (int i = 0; i < names.length; i++) {
            int x = 20 + i * 92;
            graphics.setColor(Color.decode(theme.color(names[i])));
            graphics.fillRoundRect(x, 24, 76, 76, 16, 16);
            graphics.setColor(Color.WHITE);
            graphics.drawString(names[i], x, 120);
        }
        graphics.setStroke(new BasicStroke(2f));
        graphics.dispose();
        return image;
    }

    private static String materialAt(MenuCatalog.Menu menu, MenuCatalog catalog, int slot) {
        for (MenuCatalog.Button button : menu.buttons()) {
            if (button.slot() == slot) {
                return button.material();
            }
        }
        if ("shop".equals(menu.id())) {
            for (MenuCatalog.Category category : catalog.categories()) {
                if (category.slot() == slot) {
                    return category.material();
                }
            }
        }
        if (menu.layoutSlots().contains(slot)) {
            return "CHEST";
        }
        if (menu.contentSlots().contains(slot)) {
            return "LIGHT_BLUE_STAINED_GLASS_PANE";
        }
        int row = slot / 9;
        int col = slot % 9;
        boolean border = row == 0 || col == 0 || col == 8 || row == menu.rows() - 1;
        if (border && !catalog.frame().isEmpty()) {
            return catalog.frame().get(slot % catalog.frame().size());
        }
        return catalog.filler();
    }

    private static String labelAt(MenuCatalog.Menu menu, MenuCatalog catalog, int slot) {
        for (MenuCatalog.Button button : menu.buttons()) {
            if (button.slot() == slot) {
                return button.id();
            }
        }
        if ("shop".equals(menu.id())) {
            for (MenuCatalog.Category category : catalog.categories()) {
                if (category.slot() == slot) {
                    return category.id();
                }
            }
        }
        if (menu.layoutSlots().contains(slot)) {
            return "slot";
        }
        if (menu.contentSlots().contains(slot)) {
            return "item";
        }
        return "";
    }

    private static int color(String material) {
        String name = material == null ? "" : material;
        if (name.contains("CYAN")) return 0x3DDCFF;
        if (name.contains("LIGHT_BLUE")) return 0x7AD7FF;
        if (name.contains("MAGENTA")) return 0xFF8BD2;
        if (name.contains("PURPLE")) return 0xC9B6FF;
        if (name.contains("BLUE")) return 0x6E8BFF;
        if (name.contains("GRAY") || name.contains("BLACK")) return 0x2A3142;
        if (name.contains("EMERALD") || name.contains("LIME")) return 0x7DFFB3;
        if (name.contains("SUNFLOWER") || name.contains("GOLD")) return 0xF6C453;
        if (name.contains("DIAMOND")) return 0x7AD7FF;
        if (name.contains("CHEST") || name.contains("BOOK")) return 0xC4923A;
        if (name.contains("MAP") || name.contains("COMPASS") || name.contains("FEATHER")) return 0xF6C453;
        if (name.contains("COMPARATOR") || name.contains("BARRIER") || name.contains("RED")) return 0xFF6B7A;
        if (name.contains("ARROW")) return 0xE8EEF7;
        if (name.contains("NETHER_STAR") || name.contains("PAINTING")) return 0xF4F7FF;
        if (name.contains("IRON") || name.contains("SWORD")) return 0xD9E2EC;
        if (name.contains("FIRE") || name.contains("BLAZE") || name.contains("NAME")) return 0xFF8B6A;
        if (name.contains("DRAGON") || name.contains("SKELETON")) return 0xC9B6FF;
        return 0x8B95A8;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = MenuCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration yaml(String path) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(resource(path));
        return configuration;
    }
}
