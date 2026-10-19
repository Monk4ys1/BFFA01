package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Health;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.text.TextLines;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapManager {

    private final BFFA01 plugin;
    private final List<String> availableMaps;
    private final Object timerLock = new Object();
    private String currentMap;
    private Location currentSpawn;
    private volatile boolean isSwapping = false;
    private volatile boolean rotating;
    private int ticksLeft;
    private volatile String forceNextMap;

    public MapManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.availableMaps = new ArrayList<>();
        loadMaps();
    }

    public void loadMaps() {
        synchronized (availableMaps) {
            availableMaps.clear();
            ConfigurationSection mapsSection = plugin.getConfig().getConfigurationSection("maps");
            if (mapsSection != null) {
                availableMaps.addAll(mapsSection.getKeys(false));
            }
        }
    }

    public List<String> mapNames() {
        synchronized (availableMaps) {
            return List.copyOf(availableMaps);
        }
    }

    public void startMapRotation() {
        if (mapNames().isEmpty()) {
            return;
        }

        swapMap(null);

        int swapInterval = plugin.getConfig().getInt("map-swap-interval", 300) * 20;
        synchronized (timerLock) {
            ticksLeft = swapInterval;
        }
        rotating = true;

        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        scheduler.runTimer(() -> tick(swapInterval), 20L, 20L);
    }

    private void tick(int swapInterval) {
        int left;
        synchronized (timerLock) {
            ticksLeft -= 20;
            left = ticksLeft;
        }

        if (left == 200 || left == 100 || left <= 60 && left > 0 && left % 20 == 0) {
            int secondsLeft = left / 20;
            String message = TextLines.prefixed(plugin, "map-swap-warning",
                    "&7The map will change in &e%seconds% &7seconds!",
                    java.util.Map.of("seconds", String.valueOf(secondsLeft)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                tell(player, message);
            }

            if (secondsLeft <= 3 && !isSwapping) {
                isSwapping = true;
                if (plugin.getGameListener() != null) {
                    plugin.getGameListener().clearAllBlocks();
                }
            }
        }

        if (left <= 0) {
            swapMap(null);
            synchronized (timerLock) {
                ticksLeft = swapInterval;
            }
            isSwapping = false;
        }
    }

    public void forceSwapSequence(String specificMap) {
        if (!rotating) {
            return;
        }
        synchronized (timerLock) {
            this.ticksLeft = 80;
            if (specificMap != null && hasMap(specificMap)) {
                this.forceNextMap = specificMap;
            }
        }
    }

    public void swapMap(String specificMap) {
        if (mapNames().isEmpty()) {
            return;
        }

        String next = null;
        synchronized (timerLock) {
            if (specificMap != null && mapNames().contains(specificMap)) {
                next = specificMap;
            } else if (forceNextMap != null) {
                next = forceNextMap;
                forceNextMap = null;
            }
        }
        if (next == null) {
            List<String> options = new ArrayList<>(mapNames());
            if (options.size() > 1 && currentMap != null) {
                options.remove(currentMap);
            }
            next = options.get(new Random().nextInt(options.size()));
        }
        currentMap = next;
        loadSpawn(currentMap);

        String finalMessage = TextLines.prefixed(plugin, "map-swapped",
                "&7The map has been changed to &e%map%&7!",
                java.util.Map.of("map", MapNames.sanitizeLabel(currentMap)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            ServerScheduler scheduler = plugin.scheduler();
            if (scheduler == null) {
                player.sendMessage(finalMessage);
                teleportHere(player);
            } else {
                scheduler.runOnEntity(player, () -> {
                    player.sendMessage(finalMessage);
                    teleportHere(player);
                });
            }
        }
        refreshHologram();
    }

    public void loadSpawn(String mapName) {
        ConfigurationSection mapSection = plugin.getConfig().getConfigurationSection("maps." + mapName);
        if (mapSection == null) {
            return;
        }
        String worldName = mapSection.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World '" + worldName + "' not found for map '" + mapName + "'!");
            return;
        }
        double x = mapSection.getDouble("x");
        double y = mapSection.getDouble("y");
        double z = mapSection.getDouble("z");
        float yaw = (float) mapSection.getDouble("yaw");
        float pitch = (float) mapSection.getDouble("pitch");
        currentSpawn = new Location(world, x, y, z, yaw, pitch);
    }

    public void teleportToCurrentSpawn(Player player) {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            teleportHere(player);
            return;
        }
        scheduler.runOnEntity(player, () -> teleportHere(player));
    }

    private void teleportHere(Player player) {
        if (currentSpawn == null) {
            player.sendMessage(TextLines.render(plugin, "spawn-missing", "&cSpawn location not set for this map!", java.util.Map.of()));
            return;
        }
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler != null) {
            scheduler.teleport(player, currentSpawn);
        } else {
            player.teleport(currentSpawn);
        }
        plugin.getKitManager().giveKit(player);
        Health.fill(player);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    private void refreshHologram() {
        if (!plugin.getConfig().getBoolean("holograms.enabled", false) || plugin.getHologramManager() == null || currentSpawn == null) {
            return;
        }
        Location spawn = currentSpawn.clone();
        Runnable update = () -> plugin.getHologramManager().updateHologram(spawn);
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            update.run();
        } else {
            scheduler.runAt(spawn, update, 0L);
        }
    }

    private void tell(Player player, String message) {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            player.sendMessage(message);
        } else {
            scheduler.runOnEntity(player, () -> player.sendMessage(message));
        }
    }

    public boolean hasMap(String mapName) {
        return mapName != null && mapNames().contains(mapName);
    }

    /**
     * Persists a spawn only for a safe map id. The id is a single config key
     * under maps, never a dotted path.
     */
    public boolean defineSpawn(String mapName, Location loc) {
        if (!MapNames.isValid(mapName) || loc == null || loc.getWorld() == null) {
            return false;
        }
        String base = "maps." + mapName;
        plugin.getConfig().set(base + ".world", loc.getWorld().getName());
        plugin.getConfig().set(base + ".x", loc.getX());
        plugin.getConfig().set(base + ".y", loc.getY());
        plugin.getConfig().set(base + ".z", loc.getZ());
        plugin.getConfig().set(base + ".yaw", loc.getYaw());
        plugin.getConfig().set(base + ".pitch", loc.getPitch());
        plugin.saveConfig();
        loadMaps();
        return true;
    }

    public String getCurrentMap() {
        return currentMap;
    }

    public boolean isSwapping() {
        return isSwapping;
    }
}
