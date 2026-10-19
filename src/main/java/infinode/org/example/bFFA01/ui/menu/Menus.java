package infinode.org.example.bFFA01.ui.menu;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Enchantments;
import infinode.org.example.bFFA01.shop.ShopCheckout;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.text.MiniText;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chest menus. Every control is a normal click so the same inventory works through Geyser.
 */
public final class Menus {

    private static final long KIT_COOLDOWN_NANOS = 3_000_000_000L;

    private final Presentation ui;
    private final BFFA01 plugin;
    private final ShopCheckout checkout;
    private final KeyedCooldown kitCooldown = new KeyedCooldown(KIT_COOLDOWN_NANOS);

    public Menus(Presentation ui) {
        this.ui = ui;
        this.plugin = ui.plugin();
        this.checkout = new ShopCheckout(plugin);
    }

    public boolean open(Player player, String id) {
        return open(player, id, 0, "", null, -1, true);
    }

    public boolean open(Player player, String id, int page, String arg) {
        return open(player, id, page, arg, null, -1, true);
    }

    public void click(Player player, MenuHolder holder, int slot) {
        if (player == null || holder == null) {
            return;
        }
        String action = holder.action(slot);
        if (action == null || action.isBlank() || "none".equals(action) || "page0".equals(action)) {
            return;
        }
        if (action.startsWith("kit:") && action.length() == 5 && Character.isDigit(action.charAt(4))) {
            selectKit(player, holder, action.charAt(4) - '0');
            return;
        }
        switch (action) {
            case "close" -> player.closeInventory();
            case "back" -> back(player, holder);
            case "refresh" -> open(player, holder.id(), holder.page(), holder.arg(), holder.kit(), holder.selected(), true);
            case "page:next" -> turn(player, holder, 1);
            case "page:prev" -> turn(player, holder, -1);
            case "kit:save" -> saveKit(player, holder);
            case "kit:reset" -> resetKit(player);
            case "toggle:scoreboard" -> toggleScoreboard(player);
            case "toggle:bossbar" -> toggleBossbar(player);
            case "toggle:actionbar" -> toggleActionbar(player);
            case "cycle:effect" -> cycleEffect(player);
            default -> dynamic(player, holder, action);
        }
    }

    private boolean open(Player player, String id, int page, String arg, List<String> kit, int selected, boolean chime) {
        if (player == null || id == null) {
            return false;
        }
        MenuCatalog.Menu menu = ui.catalog().menu(id);
        if (menu == null) {
            player.sendMessage(ui.line("menu-missing", Map.of()));
            return false;
        }
        String mode = arg == null ? "" : arg;
        int shownPage = Math.max(0, page);
        if ("shop".equals(id) && !"root".equals(mode) && !mode.isEmpty()) {
            shownPage = MenuPages.clamp(shownPage, pagesForOffers(mode));
        }
        Map<String, String> values = ui.placeholders(player);
        String title = MiniText.limit(ui.line(menu.titleKey(), values), 32);
        MenuHolder holder = new MenuHolder(id, shownPage, mode, menu.size(), title);
        Inventory inventory = holder.getInventory();
        background(inventory, menu);
        for (MenuCatalog.Button button : menu.buttons()) {
            if ("page0".equals(button.action()) && shownPage > 0) {
                continue;
            }
            paintButton(inventory, holder, button, values, false);
        }
        if ("shop".equals(id)) {
            paintShop(inventory, holder, menu, shownPage, mode, player);
        } else if ("kit".equals(id)) {
            paintKit(inventory, holder, menu, player, kit, selected);
        } else if ("maps".equals(id)) {
            paintMaps(inventory, holder, menu, shownPage, player);
        } else if ("stats".equals(id)) {
            paintStats(inventory, holder, menu, shownPage, player, values);
        } else if ("settings".equals(id)) {
            paintSettings(inventory, holder, player);
        }
        player.openInventory(inventory);
        if (chime) {
            ui.sound(player, "click");
        }
        return true;
    }

    private void paintShop(Inventory inventory, MenuHolder holder, MenuCatalog.Menu menu, int page, String mode, Player player) {
        if (mode.isEmpty() || "root".equals(mode)) {
            art(inventory, menu.contentSlots(), -1);
            int placed = 0;
            for (MenuCatalog.Category category : ui.catalog().categories()) {
                if (category.slot() < 0 || category.slot() >= menu.size()) {
                    continue;
                }
                ItemStack icon = item(category.material(), ui.line(category.nameKey(), Map.of()), lore(category.loreKeys(), Map.of()), false);
                inventory.setItem(category.slot(), icon);
                holder.action(category.slot(), "open:shop:" + category.id());
                placed++;
            }
            if (placed == 0) {
                art(inventory, menu.contentSlots(), 0);
            }
            return;
        }
        MenuCatalog.Category category = category(mode);
        List<ShopOffer> offers = offers(category);
        List<ShopOffer> slice = MenuPages.slice(offers, page, Math.max(1, menu.contentSlots().size()));
        art(inventory, menu.contentSlots(), -1);
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            ShopOffer offer = slice.get(i);
            int slot = menu.contentSlots().get(i);
            boolean owned = offer.permanent() && plugin.getDataManager().hasUpgrade(player.getUniqueId(), offer.id());
            inventory.setItem(slot, offer.icon(plugin, ShopOffer.key(plugin), owned));
            holder.action(slot, "buy:" + offer.id());
        }
    }

    private void paintKit(Inventory inventory, MenuHolder holder, MenuCatalog.Menu menu, Player player, List<String> supplied, int selected) {
        List<String> layout = supplied == null ? currentKit(player.getUniqueId()) : supplied;
        holder.kit(layout);
        holder.selected(selected);
        List<Integer> slots = menu.layoutSlots();
        for (int index = 0; index < slots.size() && index < layout.size(); index++) {
            int slot = slots.get(index);
            String material = layout.get(index);
            boolean air = material == null || "AIR".equals(material);
            Map<String, String> values = Map.of("slot", Integer.toString(index + 1));
            List<String> lore = new ArrayList<>();
            lore.add(ui.line("menu-kit-hint", Map.of()));
            if (index == selected) {
                lore.add(ui.line("menu-kit-selected", Map.of()));
            }
            ItemStack icon = air
                    ? item(ui.catalog().filler(), ui.line("menu-kit-empty", values), lore, false)
                    : item(material, ui.line("kit-slot", values), lore, index == selected);
            inventory.setItem(slot, icon);
            holder.action(slot, "kit:" + index);
        }
    }

    private void paintMaps(Inventory inventory, MenuHolder holder, MenuCatalog.Menu menu, int page, Player player) {
        List<String> maps = plugin.getMapManager() == null ? List.of() : plugin.getMapManager().mapNames();
        List<String> slice = MenuPages.slice(maps, page, Math.max(1, menu.contentSlots().size()));
        art(inventory, menu.contentSlots(), -1);
        if (maps.isEmpty()) {
            if (!menu.contentSlots().isEmpty()) {
                int slot = menu.contentSlots().get(0);
                inventory.setItem(slot, item("BARRIER", ui.line("menu-maps-empty", Map.of()), List.of(), false));
            }
            return;
        }
        String current = plugin.getMapManager().getCurrentMap();
        String mine = ui.votes().selection(player.getUniqueId());
        for (int i = 0; i < slice.size() && i < menu.contentSlots().size(); i++) {
            String map = slice.get(i);
            String label = MapNames.sanitizeLabel(map);
            List<String> lore = new ArrayList<>();
            lore.add(ui.line("menu-maps-votes", Map.of("votes", Integer.toString(ui.votes().count(map)))));
            lore.add(ui.line("menu-maps-click", Map.of()));
            if (map.equals(current)) {
                lore.add(ui.line("menu-maps-current", Map.of()));
            }
            if (map.equals(mine)) {
                lore.add(ui.line("menu-maps-voted", Map.of()));
            }
            String material = map.equals(current) ? "MAP" : "PAPER";
            int slot = menu.contentSlots().get(i);
            inventory.setItem(slot, item(material, ui.line("menu-map-name", Map.of("map", label)), lore, map.equals(mine)));
            holder.action(slot, "vote:" + map);
        }
    }

    private void paintStats(Inventory inventory, MenuHolder holder, MenuCatalog.Menu menu, int page, Player player, Map<String, String> values) {
        if (page <= 0) {
            return;
        }
        List<KillEntry> top = plugin.getDataManager() == null ? List.of() : plugin.getDataManager().topKills(45);
        List<KillEntry> slice = MenuPages.slice(top, page - 1, Math.max(1, menu.contentSlots().size()));
        art(inventory, menu.contentSlots(), -1);
        if (top.isEmpty() && !menu.contentSlots().isEmpty()) {
            inventory.setItem(menu.contentSlots().get(0), item("BARRIER", ui.line("menu-stats-empty", Map.of()), List.of(), false));
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
            int slot = menu.contentSlots().get(i);
            inventory.setItem(slot, item(material, ui.line("menu-stats-rank", row), List.of(ui.line("menu-stats-rank-lore", row)), rank <= 3));
            holder.action(slot, "none");
        }
    }

    private void paintSettings(Inventory inventory, MenuHolder holder, Player player) {
        UUID id = player.getUniqueId();
        var prefs = ui.preferences();
        state(inventory, holder, "scoreboard", prefs.scoreboard(id));
        state(inventory, holder, "bossbar", prefs.bossbar(id));
        state(inventory, holder, "actionbar", prefs.actionbar(id));
        String effect = prefs.effect(id);
        if (!ui.settings().effectCycle().contains(effect)) {
            effect = "totem";
        }
        int slot = slotOf("settings", "effect");
        if (slot >= 0) {
            List<String> lore = new ArrayList<>();
            lore.add(ui.line("menu-settings-effect-lore", Map.of("effect", ui.line("menu-effect-" + effect, Map.of()))));
            inventory.setItem(slot, item("FIREWORK_ROCKET", ui.line("menu-settings-effect", Map.of()), lore, !"none".equals(effect)));
            holder.action(slot, "cycle:effect");
        }
    }

    private void state(Inventory inventory, MenuHolder holder, String id, boolean enabled) {
        int slot = slotOf("settings", id);
        if (slot < 0) {
            return;
        }
        String material = switch (id) {
            case "bossbar" -> "DRAGON_HEAD";
            case "actionbar" -> "NAME_TAG";
            default -> "PAINTING";
        };
        List<String> lore = List.of(ui.line(enabled ? "menu-state-on" : "menu-state-off", Map.of()));
        inventory.setItem(slot, item(material, ui.line("menu-settings-" + id, Map.of()), lore, enabled));
        holder.action(slot, "toggle:" + id);
    }

    private void paintButton(Inventory inventory, MenuHolder holder, MenuCatalog.Button button, Map<String, String> values, boolean glow) {
        inventory.setItem(button.slot(), item(button.material(), ui.line(button.nameKey(), values), lore(button.loreKeys(), values), glow));
        holder.action(button.slot(), button.action());
    }

    private void dynamic(Player player, MenuHolder holder, String action) {
        if (action.startsWith("open:")) {
            String rest = action.substring(5);
            int split = rest.indexOf(':');
            if (split < 0) {
                open(player, rest);
            } else {
                open(player, rest.substring(0, split), 0, rest.substring(split + 1));
            }
            return;
        }
        if (action.startsWith("buy:")) {
            ShopOffer offer = ShopOffer.byId(action.substring(4));
            ShopCheckout.Result result = checkout.buy(player, offer);
            if (result == ShopCheckout.Result.BOUGHT && offer != null && offer.permanent()) {
                ui.sound(player, "buy");
                player.closeInventory();
            } else if (result == ShopCheckout.Result.BOUGHT) {
                ui.sound(player, "buy");
                open(player, "shop", holder.page(), holder.arg(), null, -1, false);
            } else {
                ui.sound(player, "fail");
            }
            return;
        }
        if (action.startsWith("vote:")) {
            String map = action.substring(5);
            var maps = plugin.getMapManager() == null ? java.util.Set.<String>of() : new java.util.HashSet<>(plugin.getMapManager().mapNames());
            String previous = ui.votes().selection(player.getUniqueId());
            if (!ui.votes().vote(player.getUniqueId(), map, maps)) {
                ui.sound(player, "fail");
                return;
            }
            String key = map.equals(previous) ? "vote-cast" : (previous == null ? "vote-cast" : "vote-changed");
            player.sendMessage(ui.prefixed(key, Map.of("map", MapNames.sanitizeLabel(map))));
            ui.sound(player, "click");
            open(player, "maps", holder.page(), "", null, -1, false);
        }
    }

    private void back(Player player, MenuHolder holder) {
        if ("shop".equals(holder.id()) && !holder.arg().isEmpty() && !"root".equals(holder.arg())) {
            open(player, "shop");
            return;
        }
        if ("hub".equals(holder.id())) {
            player.closeInventory();
            return;
        }
        open(player, "hub");
    }

    private void turn(Player player, MenuHolder holder, int delta) {
        if ("shop".equals(holder.id()) && (holder.arg().isEmpty() || "root".equals(holder.arg()))) {
            return;
        }
        if (delta < 0 && holder.page() == 0 && "shop".equals(holder.id())) {
            open(player, "shop");
            return;
        }
        int pages = switch (holder.id()) {
            case "shop" -> pagesForOffers(holder.arg());
            case "maps" -> MenuPages.count(plugin.getMapManager() == null ? 0 : plugin.getMapManager().mapNames().size(), contentSize("maps"));
            case "stats" -> 1 + MenuPages.count(plugin.getDataManager() == null ? 0 : plugin.getDataManager().topKills(45).size(), contentSize("stats"));
            default -> 1;
        };
        int next = MenuPages.clamp(holder.page() + delta, pages);
        open(player, holder.id(), next, holder.arg(), holder.kit(), holder.selected(), true);
    }

    private void selectKit(Player player, MenuHolder holder, int index) {
        if (holder.selected() < 0 || holder.selected() == index) {
            int next = holder.selected() == index ? -1 : index;
            open(player, "kit", 0, "", holder.kit(), next, true);
            return;
        }
        open(player, "kit", 0, "", KitLayout.swap(holder.kit(), holder.selected(), index), -1, true);
    }

    private void saveKit(Player player, MenuHolder holder) {
        long now = System.nanoTime();
        if (kitCooldown.remainingMillis(player.getUniqueId(), now) > 0L) {
            player.sendMessage(ui.line("kit-save-cooldown", Map.of()));
            ui.sound(player, "fail");
            return;
        }
        ItemStack[] hotbar = new ItemStack[9];
        List<String> layout = holder.kit();
        for (int i = 0; i < 9; i++) {
            String name = i < layout.size() ? layout.get(i) : "AIR";
            Material material = Material.matchMaterial(name == null ? "AIR" : name);
            hotbar[i] = material == null || material.isAir() ? null : new ItemStack(material);
        }
        if (!plugin.getDataManager().saveKitLayout(player.getUniqueId(), hotbar)) {
            player.sendMessage(ui.line("kit-save-failed", Map.of()));
            ui.sound(player, "fail");
            return;
        }
        kitCooldown.markUsed(player.getUniqueId(), now);
        player.sendMessage(ui.prefixed("kit-saved", Map.of()));
        ui.sound(player, "buy");
        player.closeInventory();
    }

    private void resetKit(Player player) {
        open(player, "kit", 0, "", KitLayout.resolve(defaultKit(), Map.of()), -1, true);
    }

    private void toggleScoreboard(Player player) {
        boolean enabled = !ui.preferences().scoreboard(player.getUniqueId());
        ui.preferences().scoreboard(player.getUniqueId(), enabled);
        if (enabled && plugin.getScoreboardManager() != null) {
            plugin.getScoreboardManager().setScoreboard(player);
        } else if (player.getServer().getScoreboardManager() != null) {
            player.setScoreboard(player.getServer().getScoreboardManager().getMainScoreboard());
        }
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
    }

    private void toggleBossbar(Player player) {
        boolean enabled = !ui.preferences().bossbar(player.getUniqueId());
        ui.preferences().bossbar(player.getUniqueId(), enabled);
        ui.refreshBossbar(player);
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
    }

    private void toggleActionbar(Player player) {
        ui.preferences().actionbar(player.getUniqueId(), !ui.preferences().actionbar(player.getUniqueId()));
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
    }

    private void cycleEffect(Player player) {
        String next = ui.settings().nextEffect(ui.preferences().effect(player.getUniqueId()));
        ui.preferences().effect(player.getUniqueId(), next);
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
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

    private int pagesForOffers(String categoryId) {
        return MenuPages.count(offers(category(categoryId)).size(), contentSize("shop"));
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

    private int slotOf(String menuId, String buttonId) {
        MenuCatalog.Menu menu = ui.catalog().menu(menuId);
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

    private void background(Inventory inventory, MenuCatalog.Menu menu) {
        List<String> frame = ui.catalog().frame();
        int frameIndex = 0;
        for (int slot = 0; slot < menu.size(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || col == 0 || col == 8 || row == menu.rows() - 1;
            String material = border && !frame.isEmpty() ? frame.get(frameIndex++ % frame.size()) : ui.catalog().filler();
            inventory.setItem(slot, pane(material));
        }
    }

    private void art(Inventory inventory, List<Integer> slots, int skip) {
        List<String> frame = ui.catalog().frame();
        if (frame.isEmpty()) {
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (i == skip) {
                continue;
            }
            inventory.setItem(slots.get(i), pane(frame.get(i % frame.size())));
        }
    }

    private List<String> lore(List<String> keys, Map<String, String> values) {
        List<String> lines = new ArrayList<>();
        for (String key : keys) {
            String line = ui.line(key, values);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private ItemStack pane(String material) {
        return item(material, MiniText.render("<#1b1f2a> ", Map.of()), List.of(), false);
    }

    private ItemStack item(String material, String name, List<String> lore, boolean glow) {
        Material type = Material.matchMaterial(material == null ? "" : material);
        if (type == null || type.isAir()) {
            type = Material.PAPER;
        }
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(name == null || name.isBlank() ? " " : name);
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (glow) {
            Enchantment enchantment = Enchantments.resolve("unbreaking");
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
