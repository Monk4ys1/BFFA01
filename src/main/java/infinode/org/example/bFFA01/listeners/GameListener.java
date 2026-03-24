package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameListener implements Listener {

    private final BFFA01 plugin;
    private final List<Block> placedBlocks;
    private final Map<UUID, Boolean> vampireFangActive = new HashMap<>();

    public GameListener(BFFA01 plugin) {
        this.plugin = plugin;
        this.placedBlocks = new ArrayList<>();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getMapManager().teleportToCurrentSpawn(player);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        int safezoneY = plugin.getConfig().getInt("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            event.setCancelled(true);
            return;
        }

        // Let the block be placed, and track it
        placedBlocks.add(block);

        int delaySeconds = plugin.getConfig().getInt("block-remove-delay", 5);
        int warningTime = Math.max(1, delaySeconds - 2);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (placedBlocks.contains(block)) {
                    block.setType(Material.REDSTONE_BLOCK);
                }
            }
        }.runTaskLater(plugin, warningTime * 20L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (placedBlocks.contains(block)) {
                    block.setType(Material.AIR);
                    placedBlocks.remove(block);
                }
            }
        }.runTaskLater(plugin, delaySeconds * 20L);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        int safeY = plugin.getConfig().getInt("death-y-level", 50);

        if (player.getLocation().getY() < safeY) {
            if (player.getHealth() > 0 && !player.isDead()) {
                player.setHealth(0.0);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                event.setCancelled(true);
                return;
            }

            int safezoneY = plugin.getConfig().getInt("safezone-y-level", 90);
            if (player.getLocation().getY() >= safezoneY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {
            Player damager = (Player) event.getDamager();

            // Vampire Fang Logic
            if (vampireFangActive.getOrDefault(damager.getUniqueId(), false)) {
                damager.setHealth(damager.getMaxHealth());
                damager.playSound(damager.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
                damager.sendMessage(ChatColor.DARK_RED + "Vampire Fang activated! You stole their life!");
                vampireFangActive.put(damager.getUniqueId(), false);
            }
        }

        // Fireball Knockback Only
        if (event.getDamager() instanceof Fireball && event.getEntity() instanceof Player) {
            Player hit = (Player) event.getEntity();
            Fireball fireball = (Fireball) event.getDamager();
            
            event.setDamage(0); // No damage
            
            // Push player away from fireball (Massive Knockback)
            Vector push = hit.getLocation().toVector().subtract(fireball.getLocation().toVector()).normalize().multiply(3.5).setY(1.2);
            hit.setVelocity(push);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player dead = event.getEntity();
        Player killer = dead.getKiller();
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] "));

        plugin.getDataManager().addDeath(dead.getUniqueId());
        plugin.getKillstreakManager().resetStreak(dead);

        if (killer != null && !killer.equals(dead)) {
            plugin.getDataManager().addKill(killer.getUniqueId());
            plugin.getDataManager().addCoins(killer.getUniqueId(), 5);
            plugin.getKillstreakManager().addKill(killer);
            
            String killMessage = plugin.getConfig().getString("messages.player-killed", "&c%player% &7was killed by &c%killer%&7.");
            String translated = ChatColor.translateAlternateColorCodes('&', killMessage.replace("%player%", dead.getName()).replace("%killer%", killer.getName()));
            event.setDeathMessage(prefix + translated);
            
            killer.setHealth(killer.getMaxHealth());
            killer.sendMessage(ChatColor.GOLD + "+5 Coins");
        } else {
            String deathMessage = plugin.getConfig().getString("messages.player-died", "&c%player% &7died.");
            String translated = ChatColor.translateAlternateColorCodes('&', deathMessage.replace("%player%", dead.getName()));
            event.setDeathMessage(prefix + translated);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead.isDead()) {
                dead.spigot().respawn();
            }
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
             plugin.getMapManager().teleportToCurrentSpawn(player);
        }, 1L);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Shop & Upgrades")) return;

        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            int safezoneY = plugin.getConfig().getInt("safezone-y-level", 90);
            if (player.getLocation().getY() < safezoneY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            event.getEntity().remove();
        } else if (event.getEntity() instanceof Snowball) {
            Snowball snowball = (Snowball) event.getEntity();
            if (snowball.getShooter() instanceof Player && event.getHitEntity() instanceof Player) {
                Player shooter = (Player) snowball.getShooter();
                Player target = (Player) event.getHitEntity();
                
                // Switcher Ball Logic
                Location sLoc = shooter.getLocation();
                Location tLoc = target.getLocation();
                shooter.teleport(tLoc);
                target.teleport(sLoc);
                shooter.playSound(shooter.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player) {
            ((Player) event.getEntity()).setFoodLevel(20);
        }
    }

    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.IN_GROUND || event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (player.getInventory().getItemInMainHand().getType() == Material.FISHING_ROD) {
                Location playerLoc = player.getLocation();
                Location hookLoc = event.getHook().getLocation();
                
                Vector velocity = hookLoc.toVector().subtract(playerLoc.toVector());
                player.setVelocity(velocity.multiply(0.25).setY(velocity.getY() * 0.15 + 0.5));
            }
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        if (event.getEntity() instanceof Fireball || event.getEntity() instanceof TNTPrimed) {
            // Cancel block damage but keep visual explosion
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getItemMeta() == null) return;
        String name = item.getItemMeta().getDisplayName();

        if (name.equals(ChatColor.RED + "Knockback Fireball") && item.getType() == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(false);
            fireball.setYield(5F); // Increased visual explosion size
            consumeItem(player);
        }
        else if (name.equals(ChatColor.YELLOW + "Jump Boost Feather") && item.getType() == Material.FEATHER) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 200, 2)); // Jump III for 10s
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
            consumeItem(player);
        }
        else if (name.equals(ChatColor.WHITE + "Speed Powder") && item.getType() == Material.SUGAR) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2)); // Speed III for 5s
            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1f, 1.5f);
            consumeItem(player);
        }
        else if (name.equals(ChatColor.DARK_GRAY + "Invisibility Cloak") && item.getType() == Material.GLASS) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 0)); // Invis for 15s
            player.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 1f);
            consumeItem(player);
        }
        else if (name.equals(ChatColor.RED + "Knockback TNT") && item.getType() == Material.TNT) {
            event.setCancelled(true);
            TNTPrimed tnt = player.getWorld().spawn(player.getLocation().add(0, 1, 0), TNTPrimed.class);
            tnt.setFuseTicks(20);
            tnt.setVelocity(player.getLocation().getDirection().multiply(1.5));
            consumeItem(player);
        }
        else if (name.equals(ChatColor.AQUA + "Rescue Platform") && item.getType() == Material.SLIME_BLOCK) {
            event.setCancelled(true);
            Location loc = player.getLocation().subtract(0, 1, 0);
            createTemporaryPlatform(loc);
            consumeItem(player);
        }
        else if (name.equals(ChatColor.DARK_RED + "Vampire Fang") && item.getType() == Material.GHAST_TEAR) {
            vampireFangActive.put(player.getUniqueId(), true);
            player.sendMessage(ChatColor.RED + "Your next hit will heal you!");
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_AMBIENT, 1f, 0.5f);
            consumeItem(player);
        }
        else if (name.equals(ChatColor.GREEN + "Player Tracker") && item.getType() == Material.COMPASS) {
            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.equals(player)) continue;
                if (p.getWorld().equals(player.getWorld())) {
                    double dist = p.getLocation().distanceSquared(player.getLocation());
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = p;
                    }
                }
            }
            if (nearest != null) {
                player.setCompassTarget(nearest.getLocation());
                player.sendMessage(ChatColor.GREEN + "Compass pointing to " + nearest.getName());
            } else {
                player.sendMessage(ChatColor.RED + "No players found.");
            }
        }
        else if (name.equals(ChatColor.GRAY + "Web Grenade") && item.getType() == Material.COBWEB) {
            event.setCancelled(true);
            Item grenade = player.getWorld().dropItem(player.getEyeLocation(), new ItemStack(Material.COBWEB));
            grenade.setVelocity(player.getLocation().getDirection().multiply(1.5));
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (grenade.isOnGround() || grenade.isDead()) {
                        Location loc = grenade.getLocation();
                        grenade.remove();
                        createTemporaryWebs(loc);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
            
            consumeItem(player);
        }
    }

    private void consumeItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void createTemporaryPlatform(Location center) {
        List<Block> newBlocks = new ArrayList<>();
        
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block b = center.clone().add(x, 0, z).getBlock();
                if (b.getType() == Material.AIR) {
                    b.setType(Material.SLIME_BLOCK);
                    newBlocks.add(b);
                    placedBlocks.add(b);
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : newBlocks) {
                    if (b.getType() == Material.SLIME_BLOCK) {
                        b.setType(Material.AIR);
                        placedBlocks.remove(b);
                    }
                }
            }
        }.runTaskLater(plugin, 100L); // 5 seconds
    }

    private void createTemporaryWebs(Location center) {
        List<Block> newBlocks = new ArrayList<>();
        
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) == 1 && Math.abs(z) == 1) continue; // Make it a cross shape
                    Block b = center.clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.AIR) {
                        b.setType(Material.COBWEB);
                        newBlocks.add(b);
                        placedBlocks.add(b);
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : newBlocks) {
                    if (b.getType() == Material.COBWEB) {
                        b.setType(Material.AIR);
                        placedBlocks.remove(b);
                    }
                }
            }
        }.runTaskLater(plugin, 100L); // 5 seconds
    }
}