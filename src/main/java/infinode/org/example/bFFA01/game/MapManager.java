package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.config.Settings;
import infinode.org.example.bFFA01.config.YamlFile;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the arena list, the rotation timer and everything players see while an
 * arena changes: chat warnings, the boss bar countdown and the swap title.
 */
public final class MapManager {

    /** Seconds the forced swap sequence runs for, so players get a warning. */
    private static final int FORCED_SWAP_COUNTDOWN = 3;

    private final BFFA01 plugin;
    private final YamlFile storage;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    private Arena current;
    private String queuedArena;
    private int secondsLeft;
    private boolean swapping;
    private BukkitTask rotationTask;
    private BossBar bossBar;

    public MapManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.storage = new YamlFile(plugin, "maps.yml", true);
    }

    // ------------------------------------------------------------------
    // Arena storage
    // ------------------------------------------------------------------

    /** Re-reads {@code maps.yml}, keeping the current arena when it survives. */
    public void loadArenas() {
        arenas.clear();
        ConfigurationSection root = storage.config().getConfigurationSection("maps");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                Arena arena = Arena.fromSection(id, root.getConfigurationSection(id), plugin.settings());
                if (arena == null) {
                    plugin.getLogger().warning("Arena '" + id + "' in maps.yml has no world and was skipped.");
                    continue;
                }
                arenas.put(id.toLowerCase(Locale.ROOT), arena);
            }
        }
        if (arenas.isEmpty()) {
            plugin.getLogger().warning("No arenas configured. Use /bffa setmap <name> while standing on a spawn.");
        } else if (current != null) {
            current = arenas.get(current.id().toLowerCase(Locale.ROOT));
        }
    }

    /** The backing {@code maps.yml}, needed by the config migrator. */
    public YamlFile storage() {
        return storage;
    }

    public List<Arena> arenas() {
        return new ArrayList<>(arenas.values());
    }

    public Optional<Arena> arena(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(arenas.get(id.toLowerCase(Locale.ROOT)));
    }

    public Arena current() {
        return current;
    }

    public String currentName() {
        return current == null ? "-" : current.displayName();
    }

    public boolean isSwapping() {
        return swapping;
    }

    public int secondsUntilSwap() {
        return Math.max(0, secondsLeft);
    }

    /** Death level of the running arena, or the configured default. */
    public int deathY() {
        return current == null ? plugin.settings().defaultDeathY() : current.deathY();
    }

    /** Safe-zone level of the running arena, or the configured default. */
    public int safezoneY() {
        return current == null ? plugin.settings().defaultSafezoneY() : current.safezoneY();
    }

    /** {@code true} when the location is at or above the arena's safe zone. */
    public boolean isInSafezone(Location location) {
        return location != null && location.getY() >= safezoneY();
    }

    public boolean isInSafezone(Player player) {
        return player != null && isInSafezone(player.getLocation());
    }

    /** Saves (or replaces) an arena and persists {@code maps.yml}. */
    public void saveArena(Arena arena) {
        arenas.put(arena.id().toLowerCase(Locale.ROOT), arena);
        arena.write(storage.config());
        storage.save();
        if (current == null) {
            swapTo(arena);
        } else if (current.id().equals(arena.id())) {
            // Editing the running arena has to take effect right away, not
            // only after the next rotation.
            current = arena;
        }
    }

    /**
     * Deletes an arena.
     *
     * @return {@code true} when it existed
     */
    public boolean deleteArena(String id) {
        Arena removed = arenas.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) {
            return false;
        }
        storage.config().set("maps." + removed.id(), null);
        storage.save();
        if (current != null && current.id().equals(removed.id())) {
            current = null;
            pickNext(null);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Rotation
    // ------------------------------------------------------------------

    public void start() {
        stop();
        loadArenas();
        if (arenas.isEmpty()) {
            return;
        }
        pickNext(null);
        secondsLeft = plugin.settings().mapSwapInterval();
        createBossBar();

        rotationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (rotationTask != null) {
            rotationTask.cancel();
            rotationTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private void tick() {
        Settings settings = plugin.settings();
        secondsLeft--;

        if (settings.mapSwapWarnings().contains(secondsLeft)) {
            plugin.messages().broadcast("map-swap-warning", "seconds", secondsLeft);
            for (Player player : Bukkit.getOnlinePlayers()) {
                Compat.playSound(player, "block.note_block.pling", 0.6F, 1.4F);
            }
        }

        if (!swapping && secondsLeft <= settings.resetBlocksBeforeSwap()) {
            swapping = true;
            plugin.blockManager().clearAll();
        }

        if (secondsLeft <= 0) {
            pickNext(queuedArena);
            queuedArena = null;
            secondsLeft = settings.mapSwapInterval();
            swapping = false;
        }

        updateBossBar();
    }

    /**
     * Starts the swap countdown right away.
     *
     * @param arenaId arena to switch to, or {@code null} for a random one
     */
    public void forceSwap(String arenaId) {
        queuedArena = arenaId;
        secondsLeft = Math.max(1, FORCED_SWAP_COUNTDOWN);
        swapping = false;
    }

    /** Picks the next arena, avoiding the current one when there is a choice. */
    private void pickNext(String preferredId) {
        Arena target = preferredId == null ? null : arenas.get(preferredId.toLowerCase(Locale.ROOT));
        if (target == null) {
            List<Arena> options = new ArrayList<>(arenas.values());
            options.removeIf(arena -> !arena.isWorldLoaded());
            if (options.isEmpty()) {
                plugin.getLogger().warning("None of the configured arenas has its world loaded.");
                return;
            }
            if (options.size() > 1 && current != null) {
                options.removeIf(arena -> arena.id().equals(current.id()));
            }
            target = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        }
        swapTo(target);
    }

    private void swapTo(Arena arena) {
        if (arena == null) {
            return;
        }
        current = arena;
        plugin.blockManager().clearAll();
        plugin.messages().broadcast("map-swapped", "map", arena.displayName());

        for (Player player : Bukkit.getOnlinePlayers()) {
            teleportToSpawn(player);
            if (plugin.settings().titlesEnabled()) {
                sendSwapTitle(player, arena);
            }
            Compat.playSound(player, "entity.player.levelup", 0.7F, 1.6F);
        }
        plugin.hologramService().refresh();
    }

    private void sendSwapTitle(Player player, Arena arena) {
        try {
            player.sendTitle(
                    plugin.messages().get("map-swap-title", "map", arena.displayName()),
                    plugin.messages().get("map-swap-subtitle", "map", arena.displayName()),
                    5, 45, 10);
        } catch (Throwable ignored) {
            // Titles are cosmetic: never let them break a map swap.
        }
    }

    /** Teleports a player to the current spawn and hands out a fresh kit. */
    public void teleportToSpawn(Player player) {
        if (current == null) {
            plugin.messages().send(player, "map-none");
            return;
        }
        Location spawn = current.spawn();
        if (spawn == null) {
            plugin.getLogger().warning("World '" + current.worldName() + "' of arena '"
                    + current.id() + "' is not loaded.");
            plugin.messages().send(player, "map-none");
            return;
        }
        player.teleport(spawn);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        Compat.heal(player);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        plugin.kitManager().giveKit(player);
        plugin.combatManager().clear(player.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Boss bar
    // ------------------------------------------------------------------

    private void createBossBar() {
        if (!plugin.settings().bossBarEnabled()) {
            return;
        }
        try {
            bossBar = Bukkit.createBossBar("", barColor(), BarStyle.SOLID);
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(player);
            }
        } catch (Throwable e) {
            plugin.getLogger().warning("Could not create the boss bar: " + e.getMessage());
            bossBar = null;
        }
    }

    /** Recreates the boss bar after a reload changed its settings. */
    public void rebuildBossBar() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        createBossBar();
        updateBossBar();
    }

    private BarColor barColor() {
        try {
            return BarColor.valueOf(plugin.settings().bossBarColor().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BarColor.BLUE;
        }
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        int interval = Math.max(1, plugin.settings().mapSwapInterval());
        double progress = Math.max(0.0D, Math.min(1.0D, (double) secondsLeft / (double) interval));
        bossBar.setProgress(progress);
        bossBar.setTitle(Text.colorize(plugin.settings().bossBarTitle()
                .replace("%time%", Text.formatTime(secondsLeft))
                .replace("%map%", currentName())));
    }

    public void showBossBar(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    public void hideBossBar(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }
}
