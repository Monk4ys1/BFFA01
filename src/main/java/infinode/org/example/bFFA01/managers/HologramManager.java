package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.stream.Collectors;

public class HologramManager {
    private static final String HOLOGRAM_TAG = "bffa_hologram";

    private final BFFA01 plugin;
    private final List<UUID> hologramUUIDs = new ArrayList<>();

    public HologramManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void updateHologram(Location spawnLoc) {
        // Remove old holograms across all worlds by UUID to ensure they are deleted even if unloaded
        for (UUID uuid : hologramUUIDs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramsClearWorldSearch();
        hologramUUIDs.clear();

        if (spawnLoc == null || spawnLoc.getWorld() == null) return;

        // Calculate top 3
        Map<String, Integer> playerKills = new HashMap<>();
        ConfigurationSection config = plugin.getDataManager().getConfig();
        for (String uuidStr : config.getKeys(false)) {
            if (uuidStr.length() == 36) { // It's a UUID
                int kills = config.getInt(uuidStr + ".kills", 0);
                String name = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                if (name == null) name = "Unknown";
                playerKills.put(name, kills);
            }
        }

        List<Map.Entry<String, Integer>> top = playerKills.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .collect(Collectors.toList());

        Location loc = spawnLoc.clone().add(0, 3.5, 0); // Above spawn

        spawnLine(loc, ChatColor.AQUA + ChatColor.BOLD.toString() + "Top Kills");
        loc.subtract(0, 0.3, 0);

        int rank = 1;
        for (Map.Entry<String, Integer> entry : top) {
            spawnLine(loc, ChatColor.YELLOW + "#" + rank + " " + ChatColor.GRAY + entry.getKey() + ": " + ChatColor.AQUA + entry.getValue());
            loc.subtract(0, 0.3, 0);
            rank++;
        }
        
        if (top.isEmpty()) {
            spawnLine(loc, ChatColor.GRAY + "No stats yet!");
        }
    }

    private void hologramsClearWorldSearch() {
        // Only remove armor stands this plugin tagged. Name matching is not
        // used: a "#" or "Top Kills" substring would delete other plugins' holograms.
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getType() == EntityType.ARMOR_STAND && entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void spawnLine(Location loc, String text) {
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        as.setGravity(false);
        as.setMarker(true);
        as.addScoreboardTag(HOLOGRAM_TAG);
        hologramUUIDs.add(as.getUniqueId());
    }
}
