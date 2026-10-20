package infinode.org.example.bFFA01.ui.menu;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.combat.DamageMath;
import infinode.org.example.bFFA01.storage.DamageEntry;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.text.MiniText;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the menu page both the chest GUI and Floodgate forms render.
 */
public final class MenuModel {

    private final Presentation ui;
    private final BFFA01 plugin;

    public MenuModel(Presentation ui) {
        this.ui = ui;
        this.plugin = ui.plugin();
    }

    public MenuView build(org.bukkit.entity.Player player, String id, int page, String arg, List<String> kit, int selected) {
        if (player == null || id == null) {
            return null;
        }
        MenuCatalog.Menu menu = ui.catalog().menu(id);
        if (menu == null) {
            return null;
        }
        String mode = arg == null ? "" : arg;
        int shown = Math.max(0, page);
        if ("shop".equals(id) && !mode.isEmpty() && !"root".equals(mode)) {
            shown = MenuPages.clamp(shown, pagesForOffers(mode));
        }
        Map<String, String> values = ui.placeholders(player);
        String title = MiniText.limit(ui.line(player, menu.titleKey(), values), 32);
        List<MenuView.Control> controls = new ArrayList<>();
        List<String> body = new ArrayList<>();
        for (MenuCatalog.Button button : menu.buttons()) {
            if ("page0".equals(button.action()) && shown > 0) {
                continue;
            }
            if ("settings".equals(id) && setting(button.id())) {
                continue;
            }
            add(controls, body, button.slot(), button.action(), button.material(),
                    ui.line(player, button.nameKey(), values), lore(button.loreKeys(), values, player), false);
        }
        List<String> layout = kit;
        int picked = selected;
        if ("shop".equals(id)) {
            shop(controls, body, menu, shown, mode, player);
        } else if ("kit".equals(id)) {
            layout = kit(controls, menu, player, kit, selected);
            picked = selected;
        } else if ("maps".equals(id)) {
            maps(controls, body, menu, shown, player);
        } else if ("stats".equals(id)) {
            stats(controls, body, menu, shown, player, values);
        } else if ("settings".equals(id)) {
            settings(controls, player);
            picked = -1;
            layout = List.of();
        }
        if (layout == null) {
            layout = List.of();
        }
        return new MenuView(id, shown, mode, menu.rows(), title, MiniText.plain(title), body, controls, layout, picked,
                ui.catalog().filler(), ui.catalog().frame(), menu.contentSlots());
    }

    private void shop(List<MenuView.Control> controls, List<String> body, MenuCatalog.Menu menu, int page, String mode,
                      org.bukkit.entity.Player player) {
        if (mode.isEmpty() || "root".equals(mode)) {
            for (MenuCatalog.Category category : ui.catalog().categories()) {
                if (category.slot() < 0 || category.slot() >= menu.size()) {
                    continue;
                }
                add(controls, body, category.slot(), "open:shop:" + category.id(), category.material(),
                        ui.line(player, category.nameKey(), Map.of()), lore(category.loreKeys(), Map.of(), player), false);
            }
            return;
        }
        MenuCatalog.Category category = category(mode);
        List<ShopOffer> offers = offers(category);
        List<ShopOffer> slice = MenuPages.slice(offers, page, Math.max(1, menu.contentSlots().size()));
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            ShopOffer offer = slice.get(i);
            boolean owned = offer.permanent() && plugin.getDataManager() != null
                    && plugin.getDataManager().hasUpgrade(player.getUniqueId(), offer.id());
            String name = offerName(offer);
            List<String> lore = offerLore(offer, owned);
            add(controls, body, menu.contentSlots().get(i), "buy:" + offer.id(), offer.material().name(), name, lore, owned);
        }
    }

    private List<String> kit(List<MenuView.Control> controls, MenuCatalog.Menu menu, org.bukkit.entity.Player player,
                             List<String> supplied, int selected) {
        List<String> layout = supplied == null ? currentKit(player.getUniqueId()) : supplied;
        List<Integer> slots = menu.layoutSlots();
        for (int index = 0; index < slots.size() && index < layout.size(); index++) {
            String material = layout.get(index);
            boolean air = material == null || "AIR".equals(material);
            Map<String, String> values = Map.of("slot", Integer.toString(index + 1));
            List<String> lore = new ArrayList<>();
            lore.add(ui.line(player, "menu-kit-hint", Map.of()));
            if (index == selected) {
                lore.add(ui.line(player, "menu-kit-selected", Map.of()));
            }
            String name = air ? ui.line(player, "menu-kit-empty", values) : ui.line(player, "kit-slot", values);
            add(controls, List.of(), slots.get(index), "kit:" + index, air ? ui.catalog().filler() : material, name, lore, index == selected);
        }
        return layout;
    }

    public List<String> freshKit() {
        return KitLayout.resolve(defaultKit(), java.util.Map.of());
    }

    private void maps(List<MenuView.Control> controls, List<String> body, MenuCatalog.Menu menu, int page, org.bukkit.entity.Player player) {
        List<String> maps = plugin.getMapManager() == null ? List.of() : plugin.getMapManager().mapNames();
        if (maps.isEmpty()) {
            if (!menu.contentSlots().isEmpty()) {
                add(controls, body, menu.contentSlots().get(0), "none", "BARRIER",
                        ui.line(player, "menu-maps-empty", Map.of()), List.of(), false);
            }
            return;
        }
        List<String> slice = MenuPages.slice(maps, page, Math.max(1, menu.contentSlots().size()));
        String current = plugin.getMapManager().getCurrentMap();
        String mine = ui.votes().selection(player.getUniqueId());
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            String map = slice.get(i);
            String label = MapNames.sanitizeLabel(map);
            List<String> lore = new ArrayList<>();
            lore.add(ui.line(player, "menu-maps-votes", Map.of("votes", Integer.toString(ui.votes().count(map)))));
            lore.add(ui.line(player, "menu-maps-click", Map.of()));
            if (map.equals(current)) {
                lore.add(ui.line(player, "menu-maps-current", Map.of()));
            }
            if (map.equals(mine)) {
                lore.add(ui.line(player, "menu-maps-voted", Map.of()));
            }
            add(controls, body, menu.contentSlots().get(i), "vote:" + map, map.equals(current) ? "MAP" : "PAPER",
                    ui.line(player, "menu-map-name", Map.of("map", label)), lore, map.equals(mine));
        }
    }

    public int firstDamagePage() {
        return 1 + killBoardPages();
    }

    private int killBoardPages() {
        int kills = plugin.getDataManager() == null ? 0 : plugin.getDataManager().topKills(45).size();
        return MenuPages.count(kills, contentSize("stats"));
    }

    private void stats(List<MenuView.Control> controls, List<String> body, MenuCatalog.Menu menu, int page,
                       org.bukkit.entity.Player player, Map<String, String> values) {
        if (page <= 0) {
            return;
        }
        int killPages = killBoardPages();
        if (page > killPages) {
            damageBoard(controls, body, menu, page - killPages, player);
            return;
        }
        List<KillEntry> top = plugin.getDataManager() == null ? List.of() : plugin.getDataManager().topKills(45);
        List<KillEntry> slice = MenuPages.slice(top, page - 1, Math.max(1, menu.contentSlots().size()));
        if (top.isEmpty() && !menu.contentSlots().isEmpty()) {
            add(controls, body, menu.contentSlots().get(0), "none", "BARRIER",
                    ui.line(player, "menu-stats-empty", Map.of()), List.of(), false);
            return;
        }
        int pageSize = Math.max(1, menu.contentSlots().size());
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            KillEntry entry = slice.get(i);
            int rank = (page - 1) * pageSize + i + 1;
            String name = entry.name() == null || entry.name().isBlank() ? entry.id().toString().substring(0, 8) : entry.name();
            Map<String, String> row = Map.of(
                    "rank", Integer.toString(rank),
                    "player", name,
                    "kills", Integer.toString(entry.kills())
            );
            String material = switch (rank) {
                case 1 -> "DIAMOND";
                case 2 -> "GOLD_INGOT";
                case 3 -> "IRON_INGOT";
                default -> "PAPER";
            };
            add(controls, body, menu.contentSlots().get(i), "none", material,
                    ui.line(player, "menu-stats-rank", row), List.of(ui.line(player, "menu-stats-rank-lore", row)), rank <= 3);
        }
    }

    private void damageBoard(List<MenuView.Control> controls, List<String> body, MenuCatalog.Menu menu, int page,
                             org.bukkit.entity.Player player) {
        List<DamageEntry> top = plugin.getDataManager() == null ? List.of() : plugin.getDataManager().topDamage(45);
        List<DamageEntry> slice = MenuPages.slice(top, page - 1, Math.max(1, menu.contentSlots().size()));
        if (top.isEmpty() && !menu.contentSlots().isEmpty()) {
            add(controls, body, menu.contentSlots().get(0), "none", "BARRIER",
                    ui.line(player, "menu-stats-damage-empty", Map.of()), List.of(), false);
            return;
        }
        int pageSize = Math.max(1, menu.contentSlots().size());
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            DamageEntry entry = slice.get(i);
            int rank = (page - 1) * pageSize + i + 1;
            String name = entry.name() == null || entry.name().isBlank() ? entry.id().toString().substring(0, 8) : entry.name();
            Map<String, String> row = Map.of(
                    "rank", Integer.toString(rank),
                    "player", name,
                    "damage", DamageMath.format(entry.hundredths())
            );
            String material = switch (rank) {
                case 1 -> "DIAMOND";
                case 2 -> "GOLD_INGOT";
                case 3 -> "IRON_INGOT";
                default -> "REDSTONE";
            };
            add(controls, body, menu.contentSlots().get(i), "none", material,
                    ui.line(player, "menu-stats-rank", row), List.of(ui.line(player, "menu-stats-damage-rank-lore", row)), rank <= 3);
        }
    }

    private void settings(List<MenuView.Control> controls, org.bukkit.entity.Player player) {
        UUID id = player.getUniqueId();
        var prefs = ui.preferences();
        state(controls, "scoreboard", prefs.scoreboard(id), player);
        state(controls, "bossbar", prefs.bossbar(id), player);
        state(controls, "actionbar", prefs.actionbar(id), player);
        state(controls, "damage", prefs.damage(id), player);
        String effect = prefs.effect(id);
        if (!ui.settings().effectCycle().contains(effect)) {
            effect = "totem";
        }
        int slot = slotOf("effect");
        if (slot >= 0) {
            List<String> lore = List.of(ui.line(player, "menu-settings-effect-lore",
                    Map.of("effect", ui.line(player, "menu-effect-" + effect, Map.of()))));
            add(controls, List.of(), slot, "cycle:effect", "FIREWORK_ROCKET",
                    ui.line(player, "menu-settings-effect", Map.of()), lore, !"none".equals(effect));
        }
    }

    private void state(List<MenuView.Control> controls, String id, boolean enabled, org.bukkit.entity.Player player) {
        int slot = slotOf(id);
        if (slot < 0) {
            return;
        }
        String material = switch (id) {
            case "bossbar" -> "DRAGON_HEAD";
            case "actionbar" -> "NAME_TAG";
            case "damage" -> "REDSTONE";
            default -> "PAINTING";
        };
        add(controls, List.of(), slot, "toggle:" + id, material,
                ui.line(player, "menu-settings-" + id, Map.of()),
                List.of(ui.line(player, enabled ? "menu-state-on" : "menu-state-off", Map.of())), enabled);
    }

    private void add(List<MenuView.Control> controls, List<String> body, int slot, String action, String material,
                     String name, List<String> lore, boolean glow) {
        String plain = MiniText.plain(name);
        if (lore != null && !lore.isEmpty()) {
            String detail = MiniText.plain(lore.get(0));
            if (!detail.isBlank() && !plain.isBlank()) {
                plain = MiniText.plain(plain + " " + detail);
            }
        }
        MenuView.Control control = new MenuView.Control(slot, action, material, name, lore, glow, plain);
        controls.add(control);
        if (!control.clickable() && body != null && !plain.isBlank()) {
            body.add(plain);
        }
    }

    private String offerName(ShopOffer offer) {
        if (plugin.getMessages() == null) {
            return offer.legacyName();
        }
        return plugin.getMessages().render("shop-" + offer.id() + "-name", Map.of());
    }

    private List<String> offerLore(ShopOffer offer, boolean owned) {
        List<String> lore = new ArrayList<>();
        if (plugin.getMessages() == null) {
            lore.add(offer.legacyName());
            return lore;
        }
        lore.add(plugin.getMessages().render("shop-cost", Map.of("cost", Integer.toString(offer.cost()))));
        if (offer.hintKey() != null) {
            lore.add(plugin.getMessages().render(offer.hintKey(), Map.of()));
        }
        String actionKey = owned && offer.permanent() ? "shop-owned" : offer.actionKey();
        String action = plugin.getMessages().render(actionKey, Map.of());
        if (!action.isEmpty()) {
            lore.add(action);
        }
        return lore;
    }

    private List<String> lore(List<String> keys, Map<String, String> values, org.bukkit.entity.Player player) {
        List<String> lines = new ArrayList<>();
        for (String key : keys) {
            String line = ui.line(player, key, values);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private boolean setting(String id) {
        return "scoreboard".equals(id) || "bossbar".equals(id) || "actionbar".equals(id) || "effect".equals(id) || "damage".equals(id);
    }

    private int slotOf(String buttonId) {
        MenuCatalog.Menu menu = ui.catalog().menu("settings");
        if (menu == null) {
            return -1;
        }
        for (MenuCatalog.Button button : menu.buttons()) {
            if (button.id().equals(buttonId)) {
                return button.slot();
            }
        }
        return -1;
    }

    private int pagesForOffers(String categoryId) {
        return MenuPages.count(offers(category(categoryId)).size(), contentSize("shop"));
    }

    public int pages(String id, String arg) {
        return switch (id) {
            case "shop" -> arg == null || arg.isEmpty() || "root".equals(arg) ? 1 : pagesForOffers(arg);
            case "maps" -> MenuPages.count(plugin.getMapManager() == null ? 0 : plugin.getMapManager().mapNames().size(), contentSize("maps"));
            case "stats" -> 1 + killBoardPages() + MenuPages.count(plugin.getDataManager() == null ? 0 : plugin.getDataManager().topDamage(45).size(), contentSize("stats"));
            default -> 1;
        };
    }

    private int contentSize(String menuId) {
        MenuCatalog.Menu menu = ui.catalog().menu(menuId);
        if (menu == null || menu.contentSlots().isEmpty()) {
            return 1;
        }
        return menu.contentSlots().size();
    }

    private MenuCatalog.Category category(String id) {
        for (MenuCatalog.Category category : ui.catalog().categories()) {
            if (category.id().equals(id)) {
                return category;
            }
        }
        return null;
    }

    private List<ShopOffer> offers(MenuCatalog.Category category) {
        if (category == null) {
            return List.of();
        }
        List<ShopOffer> offers = new ArrayList<>();
        for (String id : category.offers()) {
            ShopOffer offer = ShopOffer.byId(id);
            if (offer != null) {
                offers.add(offer);
            }
        }
        return offers;
    }

    private List<String> currentKit(UUID id) {
        Map<Integer, String> saved = new HashMap<>();
        if (plugin.getDataManager() != null) {
            for (int slot = 0; slot < 9; slot++) {
                saved.put(slot, plugin.getDataManager().getKitLayoutItem(id, slot));
            }
        }
        return KitLayout.resolve(defaultKit(), saved);
    }

    private List<String> defaultKit() {
        if (plugin.getConfig() == null) {
            return List.of();
        }
        ConfigurationSection inventory = plugin.getConfig().getConfigurationSection("kit.inventory");
        if (inventory == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(inventory.getKeys(false));
        keys.sort(Comparator.comparingInt(key -> {
            try {
                return Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                return Integer.MAX_VALUE;
            }
        }));
        List<String> materials = new ArrayList<>();
        for (String key : keys) {
            materials.add(inventory.getString(key + ".material", "AIR"));
        }
        return materials;
    }
}
