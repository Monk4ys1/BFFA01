package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.ui.Gui;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.PlayerInventory;

/** Arena protection: block rules, item rules and world rules. */
public final class ProtectionListener implements Listener {

    /** Raw slot of the off-hand in a player inventory view. */
    private static final int OFFHAND_SLOT = 40;

    private final BFFA01 plugin;

    public ProtectionListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (plugin.buildMode().isEnabled(player)) {
            return;
        }
        if (plugin.mapManager().isSwapping()) {
            event.setCancelled(true);
            plugin.messages().send(player, "build-blocked-swap");
            return;
        }
        if (plugin.mapManager().isInSafezone(player)) {
            event.setCancelled(true);
            plugin.messages().send(player, "build-blocked-safezone");
            return;
        }
        plugin.blockManager().track(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.buildMode().isEnabled(player)) {
            return;
        }
        // Players may clear away what players built, never the map itself.
        if (plugin.settings().placedBlocksBreakable() && plugin.blockManager().isTracked(event.getBlock())) {
            plugin.blockManager().untrack(event.getBlock());
            event.setDropItems(false);
            event.setExpToDrop(0);
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "build-break-blocked");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.settings().preventItemDrop() && !plugin.buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin.settings().preventOffhand() && !plugin.buildMode().isEnabled(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!plugin.settings().disableHunger() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDurability(PlayerItemDamageEvent event) {
        if (plugin.settings().disableDurability()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.settings().preventMobSpawns()) {
            return;
        }
        // Everything the plugin spawns itself uses CUSTOM.
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    /** Keeps the explosion effect but protects the arena from the crater. */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Fireball || event.getEntity() instanceof TNTPrimed) {
            event.blockList().clear();
        }
    }

    /**
     * Locks the inventory while a player is in the arena so the kit cannot be
     * rearranged mid-fight, and keeps the off-hand slot empty.
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Plugin menus handle their own clicks in GuiListener.
        if (event.getInventory().getHolder() instanceof Gui) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || plugin.buildMode().isEnabled(player)) {
            return;
        }
        if (plugin.settings().preventOffhand()
                && (event.getClick() == ClickType.SWAP_OFFHAND
                || (event.getSlot() == OFFHAND_SLOT && event.getClickedInventory() instanceof PlayerInventory))) {
            event.setCancelled(true);
            return;
        }
        if (!plugin.mapManager().isInSafezone(player)) {
            event.setCancelled(true);
        }
    }
}
