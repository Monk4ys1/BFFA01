package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.compat.Effects;
import infinode.org.example.bFFA01.shop.SpecialItem;
import infinode.org.example.bFFA01.util.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Behaviour of the consumables sold in the shop.
 *
 * <p>Items are recognised by their persistent data tag, not by their display
 * name, so renaming an item in the config cannot silently break its effect.
 */
public final class SpecialItemListener implements Listener {

    private static final int PLATFORM_LIFETIME_SECONDS = 5;
    private static final int WEB_LIFETIME_SECONDS = 5;
    /** Give up watching a thrown grenade after this many ticks. */
    private static final long GRENADE_TIMEOUT_TICKS = 200L;

    private final BFFA01 plugin;
    private final NamespacedKey projectileKey;

    public SpecialItemListener(BFFA01 plugin) {
        this.plugin = plugin;
        this.projectileKey = new NamespacedKey(plugin, "special-projectile");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        SpecialItem special = plugin.shopRegistry().identify(item);
        if (special == null) {
            return;
        }

        switch (special) {
            case FIREBALL -> {
                event.setCancelled(true);
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setIsIncendiary(false);
                fireball.setYield(5.0F);
                Compat.playSound(player, "entity.ghast.shoot", 1.0F, 1.0F);
                consume(player);
            }
            case JUMP_FEATHER -> {
                event.setCancelled(true);
                Compat.applyEffect(player, Effects.jumpBoost(), 200, 2);
                Compat.playSound(player, "entity.bat.takeoff", 1.0F, 1.0F);
                consume(player);
            }
            case SPEED_POWDER -> {
                event.setCancelled(true);
                Compat.applyEffect(player, Effects.speed(), 100, 2);
                Compat.playSound(player, "entity.witch.drink", 1.0F, 1.5F);
                consume(player);
            }
            case INVISIBILITY_CLOAK -> {
                event.setCancelled(true);
                Compat.applyEffect(player, Effects.invisibility(), 300, 0);
                Compat.playSound(player, "entity.splash_potion.break", 1.0F, 1.0F);
                consume(player);
            }
            case KNOCKBACK_TNT -> {
                event.setCancelled(true);
                TNTPrimed tnt = player.getWorld().spawn(player.getEyeLocation(), TNTPrimed.class);
                tnt.setFuseTicks(20);
                tnt.setVelocity(player.getLocation().getDirection().multiply(1.5D));
                consume(player);
            }
            case RESCUE_PLATFORM -> {
                event.setCancelled(true);
                createPlatform(player.getLocation().subtract(0.0D, 1.0D, 0.0D));
                Compat.playSound(player, "block.slime_block.place", 1.0F, 1.0F);
                consume(player);
            }
            case VAMPIRE_FANG -> {
                event.setCancelled(true);
                plugin.specialItemState().armVampireFang(player.getUniqueId());
                player.sendMessage(plugin.messages().prefix()
                        + infinode.org.example.bFFA01.util.Text.colorize("&4Your next hit will heal you."));
                Compat.playSound(player, "entity.bat.ambient", 1.0F, 0.5F);
                consume(player);
            }
            case TRACKER -> {
                event.setCancelled(true);
                trackNearest(player);
            }
            case WEB_GRENADE -> {
                event.setCancelled(true);
                throwGrenade(player);
                consume(player);
            }
            case SWITCHER_BALL -> {
                event.setCancelled(true);
                Snowball ball = player.launchProjectile(Snowball.class);
                ball.getPersistentDataContainer()
                        .set(projectileKey, PersistentDataType.STRING, SpecialItem.SWITCHER_BALL.id());
                Compat.playSound(player, "entity.snowball.throw", 1.0F, 1.0F);
                consume(player);
            }
            default -> {
                // Golden apple, ender pearl and grappling hook use vanilla behaviour.
            }
        }
    }

    /** Points the compass at the closest other player in the same world. */
    private void trackNearest(Player player) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.isDead()) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = other;
            }
        }
        if (nearest == null) {
            player.sendMessage(plugin.messages().prefix()
                    + infinode.org.example.bFFA01.util.Text.colorize("&cNobody else is in this arena."));
            Compat.playSound(player, "entity.villager.no", 0.7F, 1.0F);
            return;
        }
        player.setCompassTarget(nearest.getLocation());
        player.sendMessage(plugin.messages().prefix() + infinode.org.example.bFFA01.util.Text.colorize(
                "&aCompass now points at &f" + nearest.getName() + " &8(" + Math.round(Math.sqrt(nearestDistance)) + "m)"));
        Compat.playSound(player, "block.note_block.bell", 0.8F, 1.6F);
    }

    /** Throws a cobweb item that turns into webs where it lands. */
    private void throwGrenade(Player player) {
        Item grenade = player.getWorld().dropItem(player.getEyeLocation(),
                ItemBuilder.of(Material.COBWEB).name("&7Web Grenade").build());
        grenade.setPickupDelay(Integer.MAX_VALUE);
        grenade.setVelocity(player.getLocation().getDirection().multiply(1.5D));
        Compat.playSound(player, "entity.snowball.throw", 1.0F, 0.8F);

        new BukkitRunnable() {
            private long ticks;

            @Override
            public void run() {
                ticks++;
                if (grenade.isDead() || !grenade.isValid()) {
                    cancel();
                    return;
                }
                if (grenade.isOnGround() || ticks >= GRENADE_TIMEOUT_TICKS) {
                    Location landed = grenade.getLocation();
                    grenade.remove();
                    createWebs(landed);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 3x3 slime platform that disappears again shortly after. */
    private void createPlatform(Location center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = center.clone().add(x, 0, z).getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.SLIME_BLOCK, false);
                    plugin.blockManager().trackTemporary(block, PLATFORM_LIFETIME_SECONDS);
                }
            }
        }
    }

    /** Cross shaped web cluster, two blocks high. */
    private void createWebs(Location center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                    continue;
                }
                for (int y = 0; y <= 1; y++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType().isAir()) {
                        block.setType(Material.COBWEB, false);
                        plugin.blockManager().trackTemporary(block, WEB_LIFETIME_SECONDS);
                    }
                }
            }
        }
        Compat.playSound(center, "block.wool.place", 1.0F, 0.8F);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            // Arrows would otherwise pile up in arena walls forever.
            arrow.remove();
            return;
        }
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }
        String tag = snowball.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        if (!SpecialItem.SWITCHER_BALL.id().equals(tag)) {
            return;
        }
        if (!(snowball.getShooter() instanceof Player shooter)
                || !(event.getHitEntity() instanceof Player target)
                || shooter.equals(target)) {
            return;
        }
        Location shooterLocation = shooter.getLocation().clone();
        Location targetLocation = target.getLocation().clone();
        shooter.teleport(targetLocation);
        target.teleport(shooterLocation);
        Compat.playSound(shooter, "entity.enderman.teleport", 1.0F, 1.0F);
        Compat.playSound(target, "entity.enderman.teleport", 1.0F, 1.0F);
    }

    /** Grappling hook: pulls the player towards where the hook landed. */
    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.IN_GROUND
                && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (plugin.shopRegistry().identify(rod) != SpecialItem.GRAPPLING_HOOK) {
            return;
        }
        Vector pull = event.getHook().getLocation().toVector().subtract(player.getLocation().toVector());
        player.setVelocity(pull.multiply(0.25D).setY(pull.getY() * 0.15D + 0.5D));
        player.setFallDistance(0.0F);
        Compat.playSound(player, "entity.fishing_bobber.retrieve", 1.0F, 1.4F);
    }

    /** Removes one of the item in the player's main hand. */
    private void consume(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        player.updateInventory();
    }
}
