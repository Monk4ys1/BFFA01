package infinode.org.example.bFFA01.ui.menu;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Enchantments;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockForms;
import infinode.org.example.bFFA01.shop.ShopCheckout;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.text.MiniText;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.util.KeyedCooldown;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Opens the shared menu model as a chest on Java and a Floodgate form on Bedrock.
 */
public final class Menus {

    private static final long KIT_COOLDOWN_NANOS = 3_000_000_000L;

    private final Presentation ui;
    private final BFFA01 plugin;
    private final MenuModel model;
    private final ShopCheckout checkout;
    private final KeyedCooldown kitCooldown = new KeyedCooldown(KIT_COOLDOWN_NANOS);

    public Menus(Presentation ui) {
        this.ui = ui;
        this.plugin = ui.plugin();
        this.model = new MenuModel(ui);
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
        activate(player, holder.id(), holder.page(), holder.arg(), holder.kit(), holder.selected(), action);
    }

    private boolean open(Player player, String id, int page, String arg, List<String> kit, int selected, boolean chime) {
        if (player == null || id == null) {
            return false;
        }
        MenuView view = model.build(player, id, page, arg, kit, selected);
        if (view == null) {
            player.sendMessage(ui.line(player, "menu-missing", java.util.Map.of()));
            return false;
        }
        if (BedrockForms.open(plugin, player, view, action -> activate(player, view.id(), view.page(), view.arg(), view.kit(), view.selected(), action))) {
            if (chime) {
                ui.sound(player, "click");
            }
            return true;
        }
        MenuHolder holder = new MenuHolder(view.id(), view.page(), view.arg(), view.size(), view.title());
        holder.kit(view.kit());
        holder.selected(view.selected());
        Inventory inventory = holder.getInventory();
        background(inventory, view);
        art(inventory, view);
        for (MenuView.Control control : view.controls()) {
            if (control.slot() < 0 || control.slot() >= view.size()) {
                continue;
            }
            inventory.setItem(control.slot(), item(control.material(), control.name(), control.lore(), control.glow()));
            holder.action(control.slot(), control.action());
        }
        player.openInventory(inventory);
        if (chime) {
            ui.sound(player, "click");
        }
        return true;
    }

    private void activate(Player player, String id, int page, String arg, List<String> kit, int selected, String action) {
        if (player == null || action == null || action.isBlank() || "none".equals(action) || "page0".equals(action)) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        if (action.startsWith("kit:") && action.length() == 5 && Character.isDigit(action.charAt(4))) {
            selectKit(player, kit, action.charAt(4) - '0', selected);
            return;
        }
        switch (action) {
            case "close" -> player.closeInventory();
            case "back" -> back(player, id, arg);
            case "refresh" -> open(player, id, page, arg, kit, selected, true);
            case "page:next" -> turn(player, id, page, arg, kit, selected, 1);
            case "page:prev" -> turn(player, id, page, arg, kit, selected, -1);
            case "kit:save" -> saveKit(player, kit);
            case "kit:reset" -> open(player, "kit", 0, "", model.freshKit(), -1, true);
            case "toggle:scoreboard" -> toggleScoreboard(player);
            case "toggle:bossbar" -> toggleBossbar(player);
            case "toggle:actionbar" -> toggleActionbar(player);
            case "toggle:damage" -> toggleDamage(player);
            case "page:damage" -> open(player, "stats", model.firstDamagePage(), "", null, -1, true);
            case "cycle:effect" -> cycleEffect(player);
            default -> dynamic(player, id, page, arg, action);
        }
    }

    private void dynamic(Player player, String id, int page, String arg, String action) {
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
                open(player, "shop", page, arg, null, -1, false);
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
            String key = previous == null || map.equals(previous) ? "vote-cast" : "vote-changed";
            player.sendMessage(ui.prefixed(key, java.util.Map.of("map", MapNames.sanitizeLabel(map))));
            ui.sound(player, "click");
            open(player, "maps", page, "", null, -1, false);
        }
    }

    private void back(Player player, String id, String arg) {
        if ("shop".equals(id) && arg != null && !arg.isEmpty() && !"root".equals(arg)) {
            open(player, "shop");
            return;
        }
        if ("hub".equals(id)) {
            player.closeInventory();
            return;
        }
        open(player, "hub");
    }

    private void turn(Player player, String id, int page, String arg, List<String> kit, int selected, int delta) {
        if ("shop".equals(id) && (arg == null || arg.isEmpty() || "root".equals(arg))) {
            return;
        }
        if (delta < 0 && page == 0 && "shop".equals(id)) {
            open(player, "shop");
            return;
        }
        int pages = model.pages(id, arg);
        int next = MenuPages.clamp(page + delta, pages);
        open(player, id, next, arg, kit, selected, true);
    }

    private void selectKit(Player player, List<String> kit, int index, int selected) {
        if (selected < 0 || selected == index) {
            int next = selected == index ? -1 : index;
            open(player, "kit", 0, "", kit, next, true);
            return;
        }
        open(player, "kit", 0, "", KitLayout.swap(kit, selected, index), -1, true);
    }

    private void saveKit(Player player, List<String> layout) {
        long now = System.nanoTime();
        if (kitCooldown.remainingMillis(player.getUniqueId(), now) > 0L) {
            player.sendMessage(ui.line(player, "kit-save-cooldown", java.util.Map.of()));
            ui.sound(player, "fail");
            return;
        }
        ItemStack[] hotbar = new ItemStack[9];
        List<String> slots = layout == null ? List.of() : layout;
        for (int i = 0; i < 9; i++) {
            String name = i < slots.size() ? slots.get(i) : "AIR";
            Material material = Material.matchMaterial(name == null ? "AIR" : name);
            hotbar[i] = material == null || material.isAir() ? null : new ItemStack(material);
        }
        if (plugin.getDataManager() == null || !plugin.getDataManager().saveKitLayout(player.getUniqueId(), hotbar)) {
            player.sendMessage(ui.line(player, "kit-save-failed", java.util.Map.of()));
            ui.sound(player, "fail");
            return;
        }
        kitCooldown.markUsed(player.getUniqueId(), now);
        player.sendMessage(ui.prefixed("kit-saved", java.util.Map.of()));
        ui.sound(player, "buy");
        player.closeInventory();
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

    private void toggleDamage(Player player) {
        ui.preferences().damage(player.getUniqueId(), !ui.preferences().damage(player.getUniqueId()));
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
    }

    private void cycleEffect(Player player) {
        String next = ui.settings().nextEffect(ui.preferences().effect(player.getUniqueId()));
        ui.preferences().effect(player.getUniqueId(), next);
        ui.sound(player, "click");
        open(player, "settings", 0, "", null, -1, false);
    }

    private void background(Inventory inventory, MenuView view) {
        List<String> frame = view.frame();
        int frameIndex = 0;
        for (int slot = 0; slot < view.size(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean border = row == 0 || col == 0 || col == 8 || row == view.rows() - 1;
            String material = border && !frame.isEmpty() ? frame.get(frameIndex++ % frame.size()) : view.filler();
            inventory.setItem(slot, pane(material));
        }
    }

    private void art(Inventory inventory, MenuView view) {
        List<String> frame = view.frame();
        if (frame.isEmpty()) {
            return;
        }
        List<Integer> slots = view.contentSlots();
        for (int i = 0; i < slots.size(); i++) {
            inventory.setItem(slots.get(i), pane(frame.get(i % frame.size())));
        }
    }

    private ItemStack pane(String material) {
        return item(material, MiniText.render("<#1b1f2a> ", java.util.Map.of()), List.of(), false);
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
