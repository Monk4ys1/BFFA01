package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HologramManager {
    private static final String HOLOGRAM_TAG = "bffa_hologram";

    private final BFFA01 plugin;
    private final List<UUID> hologramUUIDs = new ArrayList<>();

    public HologramManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void clear() {
        for (UUID uuid : hologramUUIDs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        hologramUUIDs.clear();
        sweepTagged();
    }

    public void updateHologram(Location spawnLoc) {
        clear();
        if (!plugin.getConfig().getBoolean("holograms.enabled", false)) {
            return;
        }
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            return;
        }

        List<KillEntry> top = plugin.getDataManager().topKills(3);
        Location loc = spawnLoc.clone().add(0, 3.5, 0);

        spawnLine(loc, TextLines.render(plugin, "hologram-title", "&b&lTop Kills", Map.of()));
        loc.subtract(0, 0.3, 0);

        int rank = 1;
        for (KillEntry entry : top) {
            String name = entry.name() == null || entry.name().isBlank() ? "Unknown" : entry.name();
            spawnLine(loc, TextLines.render(plugin, "hologram-line", "&e#%rank% &7%player%: &b%kills%",
                    Map.of("rank", Integer.toString(rank), "player", name, "kills", Integer.toString(entry.kills()))));
            loc.subtract(0, 0.3, 0);
            rank++;
        }

        if (top.isEmpty()) {
            spawnLine(loc, TextLines.render(plugin, "hologram-empty", "&7No stats yet!", Map.of()));
        }
    }

    private void sweepTagged() {
        try {
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getType() == EntityType.ARMOR_STAND && entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                        entity.remove();
                    }
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not sweep leftover holograms: " + ex.getMessage());
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
