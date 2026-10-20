package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.api.DamageCredit;
import infinode.org.example.bFFA01.combat.DamageMath;
import infinode.org.example.bFFA01.combat.DamageSettings;
import infinode.org.example.bFFA01.combat.DamageShare;
import infinode.org.example.bFFA01.compat.Health;
import infinode.org.example.bFFA01.hooks.MatchEvents;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.hooks.worldguard.WorldGuardRegions;
import infinode.org.example.bFFA01.compat.InventoryViews;
import infinode.org.example.bFFA01.compat.PotionEffects;
import infinode.org.example.bFFA01.compat.ProfileBans;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.compat.Sounds;
import infinode.org.example.bFFA01.shop.ShopOffer;
import infinode.org.example.bFFA01.ui.menu.MenuHolder;
import infinode.org.example.bFFA01.text.TextLines;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GameListener implements Listener {

    private final BFFA01 plugin;
    private final List<TrackedPlacement> placedBlocks = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, Boolean> vampireFangActive = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatTime = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastDamager = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> combatFlags = new ConcurrentHashMap<>();
    private static final String COMBAT_LOG_BAN_SOURCE = "BuildFFA System";

    public GameListener(BFFA01 plugin) {
        this.plugin = plugin;
        startActionbarTask();
    }

    public void clearAllBlocks() {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler != null && scheduler.folia()) {
            List<TrackedPlacement> snapshot;
            synchronized (placedBlocks) {
                snapshot = new ArrayList<>(placedBlocks);
                placedBlocks.clear();
            }
            for (TrackedPlacement placement : snapshot) {
                scheduler.runAt(placement.location(), placement::forceRestore, 0L);
            }
            return;
        }
        synchronized (placedBlocks) {
            TrackedPlacement.restoreAll(placedBlocks);
        }
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
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        scheduler.runTimer(() -> {
            int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
            long pauseMillis = combatPauseSeconds * 1000L;
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runOnEntity(player, () -> refreshCombatDisplay(player, now, pauseMillis));
            }
        }, 2L, 2L);
    }

    private boolean isPlayerInCombat(Player player, long now, long pauseMillis) {
        int safezoneY = getMapSetting("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            return false;
        }
        Long lastCombat = lastCombatTime.get(player.getUniqueId());
        if (lastCombat == null) {
            return false;
        }
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

    private void refreshCombatDisplay(Player player, long now, long pauseMillis) {
        boolean inCombat = isPlayerInCombat(player, now, pauseMillis);
        combatFlags.put(player.getUniqueId(), inCombat);
        if (!inCombat) {
            lastDamager.remove(player.getUniqueId());
        }
        player.setPlayerListName((inCombat ? ChatColor.RED : ChatColor.GREEN) + player.getName());
        boolean damageLine = plugin.damageFeedback() != null && plugin.damageFeedback().holdsActionBar(player.getUniqueId(), now);
        if (damageLine) {
            // The hit line owns the action bar for a moment.
        } else if (inCombat) {
            Long lastCombat = lastCombatTime.get(player.getUniqueId());
            long remainingMillis = pauseMillis - (now - (lastCombat == null ? now : lastCombat));
            String seconds = String.format(Locale.US, "%.1f", remainingMillis / 1000.0);
            String message = TextLines.render(plugin, "combat-actionbar", "&cIn combat: &e%seconds%s",
                    Map.of("seconds", seconds));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } else if (plugin.presentation() != null) {
            plugin.presentation().idleActionBar(player);
        }
        if (plugin.presentation() != null) {
            plugin.presentation().tab(player, false);
        }

        Scoreboard board = player.getScoreboard();
        if (board == null) {
            return;
        }
        Team combatTeam = getOrCreateColoredTeam(board, "combat_red", ChatColor.RED);
        Team safeTeam = getOrCreateColoredTeam(board, "combat_safe", ChatColor.GREEN);
        for (Player target : Bukkit.getOnlinePlayers()) {
            CombatTeams.assign(combatTeam, safeTeam, target.getName(), Boolean.TRUE.equals(combatFlags.get(target.getUniqueId())));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        plugin.getDataManager().refresh(player.getUniqueId());
        if (plugin.getKillstreakManager() != null) {
            plugin.getKillstreakManager().adopt(player, plugin.getDataManager().streak(player.getUniqueId()));
        }
        if (plugin.presentation() != null) {
            plugin.presentation().importStoredPrefs(player);
        }
        plugin.getDataManager().rememberName(player.getUniqueId(), player.getName());
        event.setJoinMessage(TextLines.render(plugin, "join", "&8[&a+&8] &7%player%",
                Map.of("player", player.getName())));

        if (!plugin.getDataManager().isWritable() && player.hasPermission("bffa.admin")) {
            player.sendMessage(TextLines.render(plugin, "data-not-loaded",
                    "&cPlayer data was not loaded. data.yml was left unchanged.", Map.of()));
        }

        plugin.getMapManager().teleportToCurrentSpawn(player);
        plugin.getScoreboardManager().setScoreboard(player);
        if (plugin.presentation() != null) {
            plugin.presentation().onJoin(player);
        }
        MatchEvents.join(plugin, player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        String name = player.getName() == null ? "" : player.getName();
        if (plugin.presentation() != null) {
            plugin.presentation().onQuit(player);
        }
        forgetTeams(player, name);

        long now = System.currentTimeMillis();
        int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
        long pauseMillis = combatPauseSeconds * 1000L;

        if (isPlayerInCombat(player, now, pauseMillis)) {
            UUID damagerId = lastDamager.get(player.getUniqueId());
            Player killer = null;
            String victimName = player.getName() == null ? "" : player.getName();
            UUID victimId = player.getUniqueId();
            Location deathAt = locationOf(player);
            if (damagerId != null) {
                killer = Bukkit.getPlayer(damagerId);
            }
            Fight fight = settle(player, killer);
            plugin.getDataManager().addDeath(player.getUniqueId());
            plugin.getKillstreakManager().resetStreak(player);
            MatchEvents.death(plugin, player, null, fight.killerId, fight.killerName, fight.breakdown);
            if (plugin.damageFeedback() != null) {
                plugin.damageFeedback().summarizeDeath(player, fight.breakdown);
            }
            if (killer != null && killer.isOnline() && !killer.equals(player)) {
                Player credited = killer;
                onEntity(killer, () -> creditKill(credited, null, victimId, victimName, deathAt, true, fight.killerDamage, fight.assists));
            }

            vampireFangActive.remove(player.getUniqueId());
            lastCombatTime.remove(player.getUniqueId());
            lastDamager.remove(player.getUniqueId());
            combatFlags.remove(player.getUniqueId());
            persistSession(player);
            applyCombatLogBan(player, now);
            event.setQuitMessage(null);
            return;
        }

        vampireFangActive.remove(player.getUniqueId());
        lastCombatTime.remove(player.getUniqueId());
        lastDamager.remove(player.getUniqueId());
        combatFlags.remove(player.getUniqueId());
        if (plugin.combat() != null) {
            plugin.combat().forget(player.getUniqueId());
        }
        persistSession(player);
        event.setQuitMessage(TextLines.render(plugin, "quit", "&8[&c-&8] &7%player%",
                Map.of("player", name)));
    }

    private void forgetTeams(Player player, String name) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            onEntity(other, () -> {
                Scoreboard board = other.getScoreboard();
                if (board == null) {
                    return;
                }
                Team red = board.getTeam("combat_red");
                if (red != null) {
                    red.removeEntry(name);
                }
                Team safe = board.getTeam("combat_safe");
                if (safe != null) {
                    safe.removeEntry(name);
                }
            });
        }
    }

    private void applyCombatLogBan(Player player, long now) {
        String banTarget = player.getName();
        if (banTarget == null || banTarget.isEmpty()) {
            return;
        }
        if (ProfileBans.isBanned(player)) {
            return;
        }
        org.bukkit.BanList nameBans = Bukkit.getBanList(org.bukkit.BanList.Type.NAME);
        if (nameBans.isBanned(banTarget)) {
            return;
        }
        String reason = TextLines.render(plugin, "combat-log-kick",
                "&cYou have been kicked for Combat Logging.\nYour attacker was awarded the kill.", Map.of());
        org.bukkit.BanEntry created = nameBans.addBan(
                banTarget,
                reason,
                new java.util.Date(now + 2000L),
                COMBAT_LOG_BAN_SOURCE
        );
        if (created == null || !COMBAT_LOG_BAN_SOURCE.equals(created.getSource())) {
            return;
        }
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> pardonOwnCombatLogBan(banTarget), 10L);
        } else {
            scheduler.runLater(() -> pardonOwnCombatLogBan(banTarget), 10L);
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

        if (isPlayerInCombat(player, now, pauseMillis) && !player.hasPermission("bffa.admin")) {
            event.setCancelled(true);
            player.sendMessage(TextLines.render(plugin, "combat-command-blocked",
                    "&cYou cannot use commands while in combat!", Map.of()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getMapManager().isSwapping()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(TextLines.render(plugin, "place-blocked-swap",
                    "&cYou cannot place blocks while the map is swapping!", Map.of()));
            return;
        }

        Player player = event.getPlayer();
        int safezoneY = getMapSetting("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            event.setCancelled(true);
            return;
        }
        if (!allowsBuild(player, event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Tracks only a place that every plugin left uncancelled. Removal restores the
     * replaced block state, so a rejected place is never turned into air later.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlaceMonitor(BlockPlaceEvent event) {
        List<TrackedPlacement> scheduled;
        synchronized (placedBlocks) {
            scheduled = new ArrayList<>(TrackedPlacement.trackEvent(placedBlocks, event));
        }
        int delaySeconds = plugin.getConfig().getInt("block-remove-delay", 5);
        int warningTime = Math.max(1, delaySeconds - 2);
        for (TrackedPlacement placement : scheduled) {
            scheduleAt(placement, this::warnPlacement, warningTime * 20L);
            scheduleAt(placement, this::releasePlacement, delaySeconds * 20L);
        }
    }

    private void warnPlacement(TrackedPlacement placement) {
        synchronized (placedBlocks) {
            if (!placedBlocks.contains(placement) || !placement.shouldWarn() || !placement.stillOurs()) {
                return;
            }
            placement.applyWarning();
        }
    }

    private void releasePlacement(TrackedPlacement placement) {
        synchronized (placedBlocks) {
            if (placedBlocks.contains(placement) && placement.release()) {
                placedBlocks.removeAll(placement.members());
            }
        }
    }

    private void scheduleAt(TrackedPlacement placement, java.util.function.Consumer<TrackedPlacement> action, long delayTicks) {
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> action.accept(placement);
        if (scheduler == null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        }
        scheduler.runAt(placement.location(), task, delayTicks);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) {
            return;
        }
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        int safeY = getMapSetting("death-y-level", 50);
        Player player = event.getPlayer();
        if (to.getY() < safeY && player.getHealth() > 0 && !player.isDead()) {
            attributeVoid(player);
            player.setHealth(0.0);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            event.setCancelled(true);
            return;
        }
        int safezoneY = getMapSetting("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            Player damager = attacker(event.getDamager());
            int safezoneY = getMapSetting("safezone-y-level", 90);
            if (victim.getLocation().getY() >= safezoneY) {
                return;
            }
            if (damager != null && !damager.equals(victim) && !allowsPvp(victim.getLocation())) {
                event.setCancelled(true);
                return;
            }
            if (damager != null && !damager.equals(victim)) {
                long now = System.currentTimeMillis();
                lastCombatTime.put(victim.getUniqueId(), now);
                lastCombatTime.put(damager.getUniqueId(), now);
                lastDamager.put(victim.getUniqueId(), damager.getUniqueId());

                if (vampireFangActive.getOrDefault(damager.getUniqueId(), false)) {
                    Health.fill(damager);
                    Sounds.play(damager, 1.0f, 1.0f, "ENTITY_WITCH_DRINK");
                    damager.sendMessage(TextLines.render(plugin, "vampire-activated",
                            "&4Vampire Fang activated! You stole their life!", Map.of()));
                    vampireFangActive.put(damager.getUniqueId(), false);
                }
            }
        }

        if (event.getDamager() instanceof Fireball fireball && event.getEntity() instanceof Player hit) {
            event.setDamage(0);
            Vector push = hit.getLocation().toVector().subtract(fireball.getLocation().toVector()).normalize().multiply(3.5).setY(1.2);
            hit.setVelocity(push);
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageApplied(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            recordPlayerHit(byEntity, victim);
            return;
        }
        recordEnvironment(event, victim);
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player dead = event.getEntity();
        Player killer = dead.getKiller();
        Fight fight = settle(dead, killer);

        plugin.getDataManager().addDeath(dead.getUniqueId());
        plugin.getKillstreakManager().resetStreak(dead);
        lastCombatTime.remove(dead.getUniqueId());
        lastDamager.remove(dead.getUniqueId());
        combatFlags.put(dead.getUniqueId(), false);

        MatchEvents.death(plugin, dead, killer, fight.killerId, fight.killerName, fight.breakdown);
        if (plugin.damageFeedback() != null) {
            plugin.damageFeedback().summarizeDeath(dead, fight.breakdown);
        }
        if (killer != null && !killer.equals(dead)) {
            String victimName = dead.getName() == null ? "" : dead.getName();
            String killerName = fight.killerName.isBlank() ? (killer.getName() == null ? "" : killer.getName()) : fight.killerName;
            Location deathAt = locationOf(dead);
            UUID victimId = dead.getUniqueId();
            event.setDeathMessage(TextLines.prefixed(plugin, "player-killed",
                    "&c%player% &7was killed by &a%killer%&7.",
                    Map.of("player", victimName, "killer", killerName)));
            Player victim = dead;
            onEntity(killer, () -> creditKill(killer, victim, victimId, victimName, deathAt, false, fight.killerDamage, fight.assists));
            if (plugin.presentation() != null) {
                plugin.presentation().onDeath(dead, killerName);
            }
        } else {
            String victimName = dead.getName() == null ? "" : dead.getName();
            event.setDeathMessage(TextLines.prefixed(plugin, "player-died",
                    "&c%player% &7died.", Map.of("player", victimName)));
            if (plugin.presentation() != null) {
                plugin.presentation().onDeath(dead, "");
            }
        }

        later(dead, () -> {
            try {
                if (dead.isOnline() && dead.isDead()) {
                    dead.spigot().respawn();
                }
            } catch (Throwable ignored) {
                // The player already left.
            }
        }, 5L);
    }

    private void creditKill(Player killer, Player dead, UUID victimId, String victimName, Location deathAt, boolean announce,
                            double damage, List<DamageCredit> assists) {
        if (killer == null) {
            return;
        }
        String killerName;
        try {
            killerName = killer.getName();
        } catch (Throwable ex) {
            killerName = "";
        }
        if (killerName == null) {
            killerName = "";
        }
        String safeVictim = victimName == null ? "" : victimName;
        plugin.getDataManager().rememberName(killer.getUniqueId(), killerName);
        plugin.getDataManager().addKill(killer.getUniqueId());
        CurrencyRouter.get(plugin).rewardKill(killer);
        if (plugin.presentation() != null) {
            plugin.presentation().onKill(killer, safeVictim, deathAt);
        }
        if (plugin.getKillstreakManager() != null) {
            plugin.getKillstreakManager().addKill(killer);
        }
        if (online(killer)) {
            Health.fill(killer);
            giveKillRewards(killer);
        }
        if (announce) {
            String message = TextLines.prefixed(plugin, "player-killed",
                    "&c%player% &7was killed by &a%killer%&7.",
                    Map.of("player", safeVictim, "killer", killerName));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online == null) {
                    continue;
                }
                onEntity(online, () -> {
                    if (online.isOnline()) {
                        online.sendMessage(message);
                    }
                });
            }
        }
        MatchEvents.kill(plugin, killer, dead, safeVictim, victimId, damage, assists);
        if (plugin.damageFeedback() != null) {
            plugin.damageFeedback().summarizeKill(killer, safeVictim, damage, assists);
        }
        if (plugin.network() != null) {
            plugin.network().publishBoard();
        }
    }

    private static boolean online(Player player) {
        try {
            return player != null && player.isOnline();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static Location locationOf(Player player) {
        try {
            return player.getLocation().clone();
        } catch (Throwable ex) {
            return null;
        }
    }

    private boolean allowsBuild(Player player, Location location) {
        return regionRules() == null || regionRules().allowsBuild(plugin, location);
    }

    private boolean allowsPvp(Location location) {
        return regionRules() == null || regionRules().allowsPvp(plugin, location);
    }

    private WorldGuardRegions regionRules() {
        try {
            if (plugin.services() == null) {
                return null;
            }
            return plugin.services().get(WorldGuardRegions.class).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void giveKillRewards(Player player) {
        int defaultArrows = 0;
        Material blockMat = Material.SANDSTONE;

        if (plugin.getConfig().contains("kit.inventory")) {
            for (String key : plugin.getConfig().getConfigurationSection("kit.inventory").getKeys(false)) {
                String matStr = plugin.getConfig().getString("kit.inventory." + key + ".material");
                if (matStr != null && matStr.equals("ARROW")) {
                    defaultArrows = plugin.getConfig().getInt("kit.inventory." + key + ".amount", 64);
                } else if (matStr != null && (matStr.contains("STONE") || matStr.contains("BLOCK") || matStr.contains("WOOD") || matStr.contains("PLANKS"))) {
                    blockMat = Material.matchMaterial(matStr);
                    if (blockMat == null) {
                        blockMat = Material.SANDSTONE;
                    }
                }
            }
        }

        boolean foundArrows = false;
        boolean foundBlocks = false;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) {
                continue;
            }
            if (item.getType() == Material.ARROW) {
                item.setAmount(defaultArrows);
                foundArrows = true;
            } else if (item.getType() == blockMat) {
                item.setAmount(Math.min(64, item.getAmount() + 16));
                foundBlocks = true;
            }
        }

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
        if (player == null) {
            return;
        }
        later(player, () -> {
            if (online(player)) {
                plugin.getMapManager().teleportToCurrentSpawn(player);
            }
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
        Inventory top = InventoryViews.top(event);
        if (top != null && (top.getHolder() instanceof ShopInventoryHolder || top.getHolder() instanceof MenuHolder)) {
            event.setCancelled(true);
            return;
        }

        if (event.getSlot() == 40 || event.getClick().toString().equals("SWAP_OFFHAND")) {
            event.setCancelled(true);
            return;
        }

        if (event.getWhoClicked() instanceof Player player) {
            int safezoneY = getMapSetting("safezone-y-level", 90);
            if (player.getLocation().getY() < safezoneY) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = InventoryViews.top(event);
        if (top != null && (top.getHolder() instanceof ShopInventoryHolder || top.getHolder() instanceof MenuHolder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            event.getEntity().remove();
        } else if (event.getEntity() instanceof Snowball snowball
                && snowball.getShooter() instanceof Player shooter
                && event.getHitEntity() instanceof Player target) {
            Location sLoc = shooter.getLocation();
            Location tLoc = target.getLocation();
            ServerScheduler scheduler = plugin.scheduler();
            if (scheduler != null) {
                scheduler.teleport(shooter, tLoc);
                scheduler.teleport(target, sLoc);
            } else {
                shooter.teleport(tLoc);
                target.teleport(sLoc);
            }
            Sounds.play(shooter, 1f, 1f, "ENTITY_ENDERMAN_TELEPORT");
            Sounds.play(target, 1f, 1f, "ENTITY_ENDERMAN_TELEPORT");
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
        if (event.getEntity() instanceof Player player) {
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int safezoneY = getMapSetting("safezone-y-level", 90);
        if (player.getLocation().getY() >= safezoneY) {
            return;
        }
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            int combatPauseSeconds = plugin.getConfig().getInt("combat-regen-pause", 10);
            long pauseMillis = combatPauseSeconds * 1000L;
            Long lastCombat = lastCombatTime.get(player.getUniqueId());
            if (lastCombat != null && System.currentTimeMillis() - lastCombat < pauseMillis) {
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
            event.blockList().clear();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ShopOffer offer = offerOf(item);
        if (offer == null || offer.permanent()) {
            return;
        }
        switch (offer) {
            case FIREBALL -> {
                event.setCancelled(true);
                Fireball fireball = player.launchProjectile(Fireball.class);
                fireball.setIsIncendiary(false);
                fireball.setYield(5F);
                consumeItem(player);
            }
            case JUMP -> {
                PotionEffectType jump = PotionEffects.jumpBoost();
                if (jump != null) {
                    player.addPotionEffect(new PotionEffect(jump, 200, 2));
                }
                Sounds.play(player, 1f, 1f, "ENTITY_BAT_TAKEOFF");
                consumeItem(player);
            }
            case SPEED -> {
                PotionEffectType speed = PotionEffects.speed();
                if (speed != null) {
                    player.addPotionEffect(new PotionEffect(speed, 100, 2));
                }
                Sounds.play(player, 1f, 1.5f, "ENTITY_WITCH_DRINK");
                consumeItem(player);
            }
            case INVIS -> {
                PotionEffectType invisibility = PotionEffects.invisibility();
                if (invisibility != null) {
                    player.addPotionEffect(new PotionEffect(invisibility, 300, 0));
                }
                Sounds.play(player, 1f, 1f, "ENTITY_SPLASH_POTION_BREAK");
                consumeItem(player);
            }
            case TNT -> {
                event.setCancelled(true);
                TNTPrimed tnt = player.getWorld().spawn(player.getLocation().add(0, 1, 0), TNTPrimed.class);
                tnt.setFuseTicks(20);
                tnt.setVelocity(player.getLocation().getDirection().multiply(1.5));
                consumeItem(player);
            }
            case PLATFORM -> {
                event.setCancelled(true);
                createTemporaryPlatform(player.getLocation().subtract(0, 1, 0));
                consumeItem(player);
            }
            case VAMPIRE -> {
                vampireFangActive.put(player.getUniqueId(), true);
                player.sendMessage(TextLines.render(plugin, "vampire-ready", "&cYour next hit will heal you!", Map.of()));
                Sounds.play(player, 1f, 0.5f, "ENTITY_BAT_AMBIENT");
                consumeItem(player);
            }
            case TRACKER -> pointCompass(player);
            case WEB -> {
                event.setCancelled(true);
                throwWeb(player);
                consumeItem(player);
            }
            default -> {
                // Grapple is handled by the fishing event. The rest are plain items.
            }
        }
    }

    private ShopOffer offerOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        NamespacedKey key = ShopOffer.key(plugin);
        for (ShopOffer offer : ShopOffer.values()) {
            if (offer.matches(item, key)) {
                return offer;
            }
        }
        return null;
    }

    private void pointCompass(Player player) {
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || !other.getWorld().equals(player.getWorld())) {
                continue;
            }
            double dist = other.getLocation().distanceSquared(player.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = other;
            }
        }
        if (nearest != null) {
            player.setCompassTarget(nearest.getLocation());
            player.sendMessage(TextLines.render(plugin, "tracker-pointing", "&aCompass pointing to %player%",
                    Map.of("player", nearest.getName())));
        } else {
            player.sendMessage(TextLines.render(plugin, "tracker-none", "&cNo players found.", Map.of()));
        }
    }

    private void throwWeb(Player player) {
        Item grenade = player.getWorld().dropItem(player.getEyeLocation(), new ItemStack(Material.COBWEB));
        grenade.setVelocity(player.getLocation().getDirection().multiply(1.5));
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        boolean[] done = {false};
        ServerScheduler[] holder = new ServerScheduler[] {scheduler};
        TaskHandle handle = new TaskHandle();
        handle.cancellable = scheduler.runTimer(() -> {
            if (done[0]) {
                handle.cancellable.cancel();
                return;
            }
            holder[0].runOnEntity(grenade, () -> {
                handle.ticks++;
                if (done[0] || (!grenade.isOnGround() && !grenade.isDead() && handle.ticks <= 100)) {
                    return;
                }
                done[0] = true;
                Location loc = grenade.getLocation();
                grenade.remove();
                createTemporaryWebs(loc);
                handle.cancellable.cancel();
            });
        }, 1L, 1L);
    }

    private static final class TaskHandle {
        private infinode.org.example.bFFA01.compat.TaskRunner.Cancellable cancellable = infinode.org.example.bFFA01.compat.TaskRunner.Cancellable.NONE;
        private int ticks;
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
        List<TrackedPlacement> created = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = center.clone().add(x, 0, z).getBlock();
                if (block.getType() == Material.AIR) {
                    BlockState replaced = block.getState();
                    synchronized (placedBlocks) {
                        created.add(TrackedPlacement.track(placedBlocks, block, replaced, Material.SLIME_BLOCK));
                    }
                    block.setType(Material.SLIME_BLOCK);
                }
            }
        }
        for (TrackedPlacement placement : created) {
            scheduleAt(placement, this::releasePlacement, 100L);
        }
    }

    private void createTemporaryWebs(Location center) {
        List<TrackedPlacement> created = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (Math.abs(x) == 1 && Math.abs(z) == 1) {
                        continue;
                    }
                    Block block = center.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.AIR) {
                        BlockState replaced = block.getState();
                        synchronized (placedBlocks) {
                            created.add(TrackedPlacement.track(placedBlocks, block, replaced, Material.COBWEB));
                        }
                        block.setType(Material.COBWEB);
                    }
                }
            }
        }
        for (TrackedPlacement placement : created) {
            scheduleAt(placement, this::releasePlacement, 100L);
        }
    }

    private void recordPlayerHit(EntityDamageByEntityEvent event, Player victim) {
        Player damager = attacker(event.getDamager());
        if (damager == null || damager.equals(victim) || plugin.combat() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long hundredths = DamageMath.hundredths(event.getFinalDamage(), victim.getHealth(), absorptionTaken(event));
        if (hundredths <= 0) {
            return;
        }
        boolean crit = crit(damager);
        boolean sweep = sweep(event);
        String weapon = weapon(damager);
        plugin.combat().record(victim.getUniqueId(), damager.getUniqueId(), safeName(damager), hundredths, weapon, now, crit, sweep);
        DamageSettings settings = damageSettings();
        double remaining = Math.max(0.0, victim.getHealth() - Math.min(event.getFinalDamage(), victim.getHealth()));
        long combo = plugin.combat().combo(victim.getUniqueId(), damager.getUniqueId(), now, settings.assistMillis());
        if (plugin.damageFeedback() != null) {
            plugin.damageFeedback().showHit(damager, victim, hitLocation(victim), hundredths, remaining, combo, crit, sweep);
        }
    }

    private void recordEnvironment(EntityDamageEvent event, Player victim) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            return;
        }
        long now = System.currentTimeMillis();
        if (cause == EntityDamageEvent.DamageCause.VOID && plugin.combat() != null && plugin.combat().takeVoidSkip(victim.getUniqueId(), now)) {
            return;
        }
        if (!attributable(cause) || plugin.combat() == null) {
            return;
        }
        UUID last = lastDamager.get(victim.getUniqueId());
        Long fought = lastCombatTime.get(victim.getUniqueId());
        DamageSettings settings = damageSettings();
        if (last == null || fought == null || now - fought > settings.assistMillis()) {
            return;
        }
        long hundredths = DamageMath.hundredths(event.getFinalDamage(), victim.getHealth(), absorptionTaken(event));
        if (hundredths <= 0) {
            return;
        }
        Player damager = Bukkit.getPlayer(last);
        plugin.combat().record(victim.getUniqueId(), last, safeName(damager), hundredths, cause.name().toLowerCase(Locale.ROOT), now, false, false);
        if (plugin.damageFeedback() != null) {
            long combo = plugin.combat().combo(victim.getUniqueId(), last, now, settings.assistMillis());
            double remaining = Math.max(0.0, victim.getHealth() - Math.min(event.getFinalDamage(), victim.getHealth()));
            plugin.damageFeedback().showHit(damager, victim, hitLocation(victim), hundredths, remaining, combo, false, false);
        }
    }

    private void attributeVoid(Player player) {
        if (plugin.combat() == null) {
            return;
        }
        UUID last = lastDamager.get(player.getUniqueId());
        Long fought = lastCombatTime.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        DamageSettings settings = damageSettings();
        if (last == null || fought == null || now - fought > settings.assistMillis()) {
            return;
        }
        long hundredths = DamageMath.hundredths(player.getHealth(), player.getHealth(), 0);
        if (hundredths <= 0) {
            return;
        }
        Player damager = Bukkit.getPlayer(last);
        plugin.combat().record(player.getUniqueId(), last, safeName(damager), hundredths, "void", now, false, false);
        plugin.combat().skipNextVoid(player.getUniqueId(), now);
        if (plugin.damageFeedback() != null) {
            long combo = plugin.combat().combo(player.getUniqueId(), last, now, settings.assistMillis());
            plugin.damageFeedback().showHit(damager, player, hitLocation(player), hundredths, 0, combo, false, false);
        }
    }

    private Fight settle(Player dead, Player killer) {
        DamageSettings settings = damageSettings();
        long now = System.currentTimeMillis();
        List<DamageShare> shares = plugin.combat() == null
                ? List.of()
                : plugin.combat().settle(dead.getUniqueId(), now, settings.assistMillis());
        UUID killerId = null;
        String killerName = "";
        if (killer != null) {
            try {
                killerId = killer.getUniqueId();
                killerName = killer.getName() == null ? "" : killer.getName();
            } catch (Throwable ignored) {
                killerName = "";
            }
        }
        double killerDamage = 0;
        List<DamageCredit> assists = new ArrayList<>();
        List<DamageCredit> breakdown = new ArrayList<>();
        for (DamageShare share : shares) {
            if (plugin.getDataManager() != null) {
                plugin.getDataManager().addCombatDamage(share.attacker(), dead.getUniqueId(), share.hundredths());
            }
            DamageCredit credit = new DamageCredit(share.attacker(), share.label(), DamageMath.hearts(share.hundredths()), weaponLabel(share.weapon()));
            breakdown.add(credit);
            if (killerId != null && killerId.equals(share.attacker())) {
                killerDamage = credit.damage();
            } else if (share.hundredths() >= settings.minimumHundredths() && plugin.getDataManager() != null) {
                plugin.getDataManager().addAssist(share.attacker());
                assists.add(credit);
            }
        }
        if (plugin.getDataManager() != null) {
            plugin.getDataManager().resetLife(dead.getUniqueId());
        }
        return new Fight(killerId, killerName, killerDamage, List.copyOf(assists), List.copyOf(breakdown));
    }

    private void persistSession(Player player) {
        if (player == null || plugin.getDataManager() == null) {
            return;
        }
        UUID id = player.getUniqueId();
        if (plugin.getKillstreakManager() != null) {
            plugin.getDataManager().setStreak(id, plugin.getKillstreakManager().getStreak(player));
        }
        if (plugin.presentation() != null) {
            plugin.presentation().exportPrefs(player);
        }
        plugin.getDataManager().handoff(id);
    }

    private DamageSettings damageSettings() {
        if (plugin.damageFeedback() != null) {
            return plugin.damageFeedback().settings();
        }
        return DamageSettings.load(null, null);
    }

    private static double absorptionTaken(EntityDamageEvent event) {
        try {
            Class<?> modifier = Class.forName("org.bukkit.event.entity.EntityDamageEvent$DamageModifier");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object absorption = Enum.valueOf((Class) modifier, "ABSORPTION");
            java.lang.reflect.Method applicable = event.getClass().getMethod("isDamageModifierApplicable", modifier);
            if (!Boolean.TRUE.equals(applicable.invoke(event, absorption))) {
                return 0;
            }
            java.lang.reflect.Method get = event.getClass().getMethod("getDamage", modifier);
            Object value = get.invoke(event, absorption);
            if (value instanceof Number number && number.doubleValue() < 0) {
                return -number.doubleValue();
            }
        } catch (Throwable ignored) {
            return 0;
        }
        return 0;
    }

    private static boolean crit(Player attacker) {
        try {
            return attacker.getFallDistance() > 0.0f
                    && !attacker.isOnGround()
                    && !attacker.isInWater()
                    && !attacker.isClimbing()
                    && !attacker.isInsideVehicle();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static boolean sweep(EntityDamageByEntityEvent event) {
        try {
            return "ENTITY_SWEEP_ATTACK".equals(event.getCause().name());
        } catch (Throwable ex) {
            return false;
        }
    }

    private static String weapon(Player attacker) {
        try {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                return "";
            }
            return hand.getType().name();
        } catch (Throwable ex) {
            return "";
        }
    }

    private static String weaponLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String safeName(Player player) {
        if (player == null) {
            return "";
        }
        try {
            return player.getName() == null ? "" : player.getName();
        } catch (Throwable ex) {
            return "";
        }
    }

    private static Location hitLocation(Player victim) {
        try {
            return victim.getEyeLocation().clone();
        } catch (Throwable ex) {
            return locationOf(victim);
        }
    }

    private static boolean attributable(EntityDamageEvent.DamageCause cause) {
        if (cause == null) {
            return false;
        }
        return switch (cause) {
            case VOID, LAVA, FIRE, FIRE_TICK, DROWNING, SUFFOCATION, CONTACT, HOT_FLOOR, WITHER, POISON,
                    FREEZE, CRAMMING, STARVATION, MAGIC, DRAGON_BREATH, FALLING_BLOCK -> true;
            default -> false;
        };
    }

    private record Fight(UUID killerId, String killerName, double killerDamage, List<DamageCredit> assists, List<DamageCredit> breakdown) {
    }

    private void onEntity(Player player, Runnable task) {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.runOnEntity(player, task);
        }
    }

    private void later(Player player, Runnable task, long delayTicks) {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        } else {
            scheduler.runOnEntityLater(player, task, delayTicks);
        }
    }
}
