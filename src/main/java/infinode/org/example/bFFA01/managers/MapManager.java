package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapManager {

    private final BFFA01 plugin;
    private final List<String> availableMaps;
    private String currentMap;
    private Location currentSpawn;

    public MapManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.availableMaps = new ArrayList<>();
        loadMaps();
    }

    public void loadMaps() {
        availableMaps.clear();
        ConfigurationSection mapsSection = plugin.getConfig().getConfigurationSection("maps");
        if (mapsSection != null) {
            availableMaps.addAll(mapsSection.getKeys(false));
        }
    }

    public void startMapRotation() {
        if (availableMaps.isEmpty()) return;
        
        // Pick an initial map
        swapMap();

        int swapInterval = plugin.getConfig().getInt("map-swap-interval", 300) * 20; // in ticks

        new BukkitRunnable() {
            int ticksLeft = swapInterval;

            @Override
            public void run() {
                ticksLeft -= 20; // 1 second passed

                // Announce at 10, 5, 3, 2, 1 seconds
                if (ticksLeft == 200 || ticksLeft == 100 || ticksLeft <= 60 && ticksLeft > 0 && ticksLeft % 20 == 0) {
                    int secondsLeft = ticksLeft / 20;
                    String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
                    String warning = plugin.getConfig().getString("messages.map-swap-warning", "&7The map will change in &e%seconds% &7seconds!");
                    String message = ChatColor.translateAlternateColorCodes('&', prefix + warning.replace("%seconds%", String.valueOf(secondsLeft)));
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(message);
                    }
                }

                if (ticksLeft <= 0) {
                    swapMap();
                    ticksLeft = swapInterval; // Reset timer
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every 1 second (20 ticks)
    }

    public void swapMap() {
        if (availableMaps.isEmpty()) return;

        List<String> options = new ArrayList<>(availableMaps);
        if (options.size() > 1 && currentMap != null) {
            options.remove(currentMap);
        }

        Random random = new Random();
        String nextMap = options.get(random.nextInt(options.size()));
        
        currentMap = nextMap;
        loadSpawn(currentMap);

        plugin.getHologramManager().updateHologram(currentSpawn);

        String message = plugin.getConfig().getString("messages.map-swapped", "&7The map has been changed to &e%map%&7!");
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bBuildFFA&8] ");
        String finalMessage = ChatColor.translateAlternateColorCodes('&', prefix + message.replace("%map%", currentMap));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(finalMessage);
            teleportToCurrentSpawn(player);
        }
    }

    public void loadSpawn(String mapName) {
        ConfigurationSection mapSection = plugin.getConfig().getConfigurationSection("maps." + mapName);
        if (mapSection != null) {
            String worldName = mapSection.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = mapSection.getDouble("x");
                double y = mapSection.getDouble("y");
                double z = mapSection.getDouble("z");
                float yaw = (float) mapSection.getDouble("yaw");
                float pitch = (float) mapSection.getDouble("pitch");
                currentSpawn = new Location(world, x, y, z, yaw, pitch);
            } else {
                plugin.getLogger().warning("World '" + worldName + "' not found for map '" + mapName + "'!");
            }
        }
    }

    public void teleportToCurrentSpawn(Player player) {
        if (currentSpawn != null) {
            player.teleport(currentSpawn);
            plugin.getKitManager().giveKit(player);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        } else {
            player.sendMessage(ChatColor.RED + "Spawn location not set for this map!");
        }
    }

    public String getCurrentMap() {
        return currentMap;
    }
}
