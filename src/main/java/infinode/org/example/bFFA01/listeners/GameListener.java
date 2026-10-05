package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
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
    private final Map<UUID, Long> lastCombatTime = new HashMap<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private static final String COMBAT_LOG_BAN_SOURCE = "BuildFFA System";

    public GameListener(BFFA01 plugin) {
        this.plugin = plugin;
        this.placedBlocks = new ArrayList<>();
        startActionbarTask();
    }

    public void clearAllBlocks() {
        for (Block block : placedBlocks) {
            block.setType(Material.AIR);
        }
        placedBlocks.clear();
    }

    private int getMapSetting(String key, int fallbackKey) {
        String mapName = plugin.getMapManager().getCurrentMap();
        int fallback = plugin.getConfig().getInt(fallbackKey == 0 ? "default-death-y-level" : "default-safezone-y-level", fallbackKey);
        if (mapName != null && plugin.getConfig().contains("maps." + mapName + "." + key)) {
            return plugin.getConfig().getInt("maps." + mapName + "." + key);
        }
        return fallback;
    }

    private void startActionbarTask() {
        BukkitRunnable actionbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
                long pauseMillis = combatPauseSeconds * 1000L;
                long now = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isPlayerInCombat(player, now, pauseMillis)) {
                        long lastCombat = lastCombatTime.get(player.getUniqueId());
                        long remainingMillis = pauseMillis - (now - lastCombat);
                        double remainingSeconds = remainingMillis / 1000.0;
                        String message = ChatColor.RED + "In combat: " + ChatColor.YELLOW + String.format("%.1f", remainingSeconds) + "s";
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    } else {
                        // Clear combat tracking if it expired to avoid old damagers counting
                        lastDamager.remove(player.getUniqueId());
                    }
                }

                syncCombatNameDisplays(now, pauseMillis);
            }
        };
        actionbarTask.runTaskTimer(plugin, 2L, 2L); // Run every 2 ticks (0.1s) for smooth updates
    }

    private boolean isPlayerInCombat(Player player, long now, long pauseMillis) {
        int safezoneY = getMapSetting("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            return false;
        }
        if (!lastCombatTime.containsKey(player.getUniqueId())) {
            return false;
        }
        long lastCombat = lastCombatTime.get(player.getUniqueId());
        return (now - lastCombat) < pauseMillis;
    }

    private Team getOrCreateColoredTeam(Scoreboard board, String teamId, ChatColor color) {
        Team existing = board.getTeam(teamId);
        if (existing != null) {
            return existing;
        }
        Team team = board.registerNewTeam(teamId);
        try {
            team.setColor(color);
        } catch (NoSuchMethodError e) {
            team.setPrefix(color.toString());
        }
        return team;
    }

    /**
     * Tab list names and nametag team colors: red in combat, green otherwise.
     * Each viewer's scoreboard gets team entries for every online player so colors are visible to all.
     */
    private void syncCombatNameDisplays(long now, long pauseMillis) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) {
            return;
        }

        Map<String, Boolean> inCombatByName = new HashMap<>();
        for (Player p : online) {
            boolean inCombat = isPlayerInCombat(p, now, pauseMillis);
            inCombatByName.put(p.getName(), inCombat);
            
            // Tablist format
            if (inCombat) {
                p.setPlayerListName(ChatColor.RED + p.getName());
            } else {
                p.setPlayerListName(ChatColor.GREEN + p.getName());
            }
        }

        for (Player viewer : online) {
            Scoreboard board = viewer.getScoreboard();
            if (board == null) {
                continue;
            }
            Team combatTeam = getOrCreateColoredTeam(board, "combat_red", ChatColor.RED);
            Team safeTeam = getOrCreateColoredTeam(board, "combat_safe", ChatColor.GREEN);
            
            for (Player target : online) {
                String name = target.getName();
                boolean inCombat = inCombatByName.get(name);
                if (inCombat) {
                    if (!combatTeam.hasEntry(name)) {
                        combatTeam.addEntry(name);
                    }
                } else {
                    if (!safeTeam.hasEntry(name)) {
                        safeTeam.addEntry(name);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Custom Join Message
        String joinMsg = plugin.getConfig().getString("messages.join", "&8[&a+&8] &7%player%");
        event.setJoinMessage(ChatColor.translateAlternateColorCodes('&', joinMsg.replace("%player%", player.getName())));

        plugin.getMapManager().teleportToCurrentSpawn(player);
        plugin.getScoreboardManager().setScoreboard(player);
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        // Since we unban them immediately, this allows them to join right back.
        // We catch them here just in case Bukkit hasn't processed the unban yet
        // but normally it's fast enough. We don't need to do anything special.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            Scoreboard board = other.getScoreboard();
            if (board == null) {
                continue;
            }
            Team red = board.getTeam("combat_red");
            if (red != null) {
                red.removeEntry(name);
            }
            Team safe = board.getTeam("combat_safe");
            if (safe != null) {
                safe.removeEntry(name);
            }
        }

        // Handle Combat Logging
        long now = System.currentTimeMillis();
        int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
        long pauseMillis = combatPauseSeconds * 1000L;
        String prefix = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] "));
        
        if (isPlayerInCombat(player, now, pauseMillis)) {
            UUID damagerId = lastDamager.get(player.getUniqueId());
            if (damagerId != null) {
                Player killer = Bukkit.getPlayer(damagerId);
                if (killer != null && killer.isOnline() && !killer.equals(player)) {
                    // Credit kill to the last person who hit them
                    plugin.getDataManager().addKill(killer.getUniqueId());
                    plugin.getDataManager().addCoins(killer.getUniqueId(), 5);
                    plugin.getKillstreakManager().addKill(killer);
                    
                    String killMessage = plugin.getConfig().getString("messages.player-killed", "&c%player% &7was killed by &a%killer%&7.");
                    String translated = ChatColor.translateAlternateColorCodes('&', killMessage.replace("%player%", player.getName()).replace("%killer%", killer.getName()));
                    Bukkit.broadcastMessage(prefix + translated);
                    
                    killer.setHealth(killer.getMaxHealth());
                    giveKillRewards(killer);
                }
            }
            
            plugin.getDataManager().addDeath(player.getUniqueId());
            plugin.getKillstreakManager().resetStreak(player);

            // Memory Leak Prevention BEFORE the kick delay logic to ensure it runs
            vampireFangActive.remove(player.getUniqueId());
            lastCombatTime.remove(player.getUniqueId());
            lastDamager.remove(player.getUniqueId());

            // Short name ban so the disconnect screen shows the combat-log reason.
            // Never replace or pardon a ban this plugin did not just create.
            applyCombatLogBan(player, now);
            
            // Since they are combat logging, we don't want to broadcast the normal quit message
            event.setQuitMessage(null);
            return;
        }

        // Normal Quit Clean up maps
        vampireFangActive.remove(player.getUniqueId());
        lastCombatTime.remove(player.getUniqueId());
        lastDamager.remove(player.getUniqueId());

        // Custom Quit Message
        String quitMsg = plugin.getConfig().getString("messages.quit", "&8[&c-&8] &7%player%");
        event.setQuitMessage(ChatColor.translateAlternateColorCodes('&', quitMsg.replace("%player%", player.getName())));
    }

    private void applyCombatLogBan(Player player, long now) {
        String banTarget = player.getName();
        if (banTarget == null || banTarget.isEmpty()) {
            return;
        }
        if (isProfileBanned(player)) {
            return;
        }
        org.bukkit.BanList nameBans = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
        if (nameBans.isBanned(banTarget)) {
            return;
        }
        org.bukkit.BanEntry created = nameBans.addBan(
                banTarget,
                ChatColor.RED + "You have been kicked for Combat Logging.\nYour attacker was awarded the kill.",
                new java.util.Date(now + 2000L),
                COMBAT_LOG_BAN_SOURCE
        );
        if (created == null || !COMBAT_LOG_BAN_SOURCE.equals(created.getSource())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> pardonOwnCombatLogBan(banTarget), 10L);
    }

    private boolean isProfileBanned(Player player) {
        try {
            org.bukkit.profile.PlayerProfile profile = player.getPlayerProfile();
            if (profile == null || profile.getUniqueId() == null) {
                return false;
            }
            org.bukkit.ban.ProfileBanList profiles = Bukkit.getBanList(org.bukkit.BanList.Type.PROFILE);
            return profiles.isBanned(profile);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not check profile ban for " + player.getName() + ": " + ex.getMessage());
            return true;
        }
    }

    private void pardonOwnCombatLogBan(String banTarget) {
        org.bukkit.BanList nameBans = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
        org.bukkit.BanEntry entry = nameBans.getBanEntry(banTarget);
        if (entry == null || !COMBAT_LOG_BAN_SOURCE.equals(entry.getSource())) {
            return;
        }
        java.util.Date expires = entry.getExpiration();
        long remaining = expires == null ? Long.MAX_VALUE : expires.getTime() - System.currentTimeMillis();
        if (expires != null && remaining <= 2500L) {
            nameBans.pardon(banTarget);
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
        long pauseMillis = combatPauseSeconds * 1000L;
        
        // Block commands during combat so players cannot escape with /hub or /spawn.
        // Staff keep command access so a combat tag cannot lock out moderation.
        if (isPlayerInCombat(player, now, pauseMillis) && !player.hasPermission("bffa.admin")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use commands while in combat!");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getMapManager().isSwapping()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot place blocks while the map is swapping!");
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        int safezoneY = getMapSetting("safezone-y-level", 90);
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
        int safeY = getMapSetting("death-y-level", 50);

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

            int safezoneY = getMapSetting("safezone-y-level", 90);
            if (player.getLocation().getY() >= safezoneY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player damager = null;
            
            if (event.getDamager() instanceof Player) {
                damager = (Player) event.getDamager();
            } else if (event.getDamager() instanceof Projectile) {
                Projectile proj = (Projectile) event.getDamager();
                if (proj.getShooter() instanceof Player) {
                    damager = (Player) proj.getShooter();
                }
            }

            int safezoneY = getMapSetting("safezone-y-level", 90);
            if (victim.getLocation().getY() >= safezoneY) {
                return; // Combat cooldown doesn't apply above safezone
            }

            if (damager != null && !damager.equals(victim)) {
                // Update combat time for both players
                long now = System.currentTimeMillis();
                lastCombatTime.put(victim.getUniqueId(), now);
                lastCombatTime.put(damager.getUniqueId(), now);
                
                // Track who hit the victim last for combat logging
                lastDamager.put(victim.getUniqueId(), damager.getUniqueId());

                // Vampire Fang Logic
                if (vampireFangActive.getOrDefault(damager.getUniqueId(), false)) {
                    damager.setHealth(damager.getMaxHealth());
                    damager.playSound(damager.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
                    damager.sendMessage(ChatColor.DARK_RED + "Vampire Fang activated! You stole their life!");
                    vampireFangActive.put(damager.getUniqueId(), false);
                }
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
    public void onItemDamage(PlayerItemDamageEvent event) {
        // Prevent armor and weapons from losing durability
        event.setCancelled(true);
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
        
        // Remove from combat tag on death
        lastCombatTime.remove(dead.getUniqueId());
        lastDamager.remove(dead.getUniqueId());
        syncCombatNameDisplays(System.currentTimeMillis(), plugin.getConfig().getInt("combat-regen-pause", 10) * 1000L);

        if (killer != null && !killer.equals(dead)) {
            plugin.getDataManager().addKill(killer.getUniqueId());
            plugin.getDataManager().addCoins(killer.getUniqueId(), 5);
            plugin.getKillstreakManager().addKill(killer);
            
            String killMessage = plugin.getConfig().getString("messages.player-killed", "&c%player% &7was killed by &a%killer%&7.");
            String translated = ChatColor.translateAlternateColorCodes('&', killMessage.replace("%player%", dead.getName()).replace("%killer%", killer.getName()));
            event.setDeathMessage(prefix + translated);
            
            killer.setHealth(killer.getMaxHealth());

            // Give blocks and reset arrows on kill
            giveKillRewards(killer);

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

    private void giveKillRewards(Player player) {
        // Find default arrow amount from config
        int defaultArrows = 0;
        Material blockMat = Material.SANDSTONE; // fallback

        if (plugin.getConfig().contains("kit.inventory")) {
            for (String key : plugin.getConfig().getConfigurationSection("kit.inventory").getKeys(false)) {
                String matStr = plugin.getConfig().getString("kit.inventory." + key + ".material");
                if (matStr != null && matStr.equals("ARROW")) {
                    defaultArrows = plugin.getConfig().getInt("kit.inventory." + key + ".amount", 64);
                } else if (matStr != null && (matStr.contains("STONE") || matStr.contains("BLOCK") || matStr.contains("WOOD") || matStr.contains("PLANKS"))) {
                    blockMat = Material.matchMaterial(matStr);
                    if (blockMat == null) blockMat = Material.SANDSTONE;
                }
            }
        }

        boolean foundArrows = false;
        boolean foundBlocks = false;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null) {
                if (item.getType() == Material.ARROW) {
                    item.setAmount(defaultArrows);
                    foundArrows = true;
                } else if (item.getType() == blockMat) {
                    int newAmt = Math.min(64, item.getAmount() + 16);
                    item.setAmount(newAmt);
                    foundBlocks = true;
                }
            }
        }

        // If they ran completely out of arrows or blocks, we need to add them anew.
        if (!foundArrows && defaultArrows > 0) {
            player.getInventory().addItem(new ItemStack(Material.ARROW, defaultArrows));
        }
        if (!foundBlocks) {
            player.getInventory().addItem(new ItemStack(blockMat, 16));
        }
        
        player.updateInventory();
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
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopInventoryHolder) return;

        // Prevent putting items in the offhand slot directly or via shortcut
        if (event.getSlot() == 40 || event.getClick().toString().equals("SWAP_OFFHAND")) {
            event.setCancelled(true);
            return;
        }

        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            int safezoneY = getMapSetting("safezone-y-level", 90);
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
            Player player = (Player) event.getEntity();
            
            int safezoneY = getMapSetting("safezone-y-level", 90);
            if (player.getLocation().getY() >= safezoneY) {
                return; // Allow natural regeneration in safezone
            }

            // Only cancel natural regeneration (satiated/magic regen like golden apples etc. will not be affected unless intended)
            // Note: Food level is forced to 20, so natural regen happens via SATIATED
            if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
                
                int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
                long pauseMillis = combatPauseSeconds * 1000L;
                
                if (lastCombatTime.containsKey(player.getUniqueId())) {
                    long lastCombat = lastCombatTime.get(player.getUniqueId());
                    if (System.currentTimeMillis() - lastCombat < pauseMillis) {
                        event.setCancelled(true);
                    }
                }
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
