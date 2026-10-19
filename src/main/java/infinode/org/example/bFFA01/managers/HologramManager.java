package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.hologram.HologramHub;
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
        clearExternal();
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

        List<String> lines = lines();
        if (external(spawnLoc, lines)) {
            return;
        }
        Location loc = spawnLoc.clone().add(0, 3.5, 0);
        for (String line : lines) {
            spawnLine(loc, line);
            loc.subtract(0, 0.3, 0);
        }
    }

    private List<String> lines() {
        List<String> lines = new ArrayList<>();
        List<KillEntry> top = plugin.getDataManager().topKills(3);
        lines.add(TextLines.render(plugin, "hologram-title", "&b&lTop Kills", Map.of()));
        int rank = 1;
        for (KillEntry entry : top) {
            String name = entry.name() == null || entry.name().isBlank() ? "Unknown" : entry.name();
            lines.add(TextLines.render(plugin, "hologram-line", "&e#%rank% &7%player%: &b%kills%",
                    Map.of("rank", Integer.toString(rank), "player", name, "kills", Integer.toString(entry.kills()))));
            rank++;
        }
        if (top.isEmpty()) {
            lines.add(TextLines.render(plugin, "hologram-empty", "&7No stats yet!", Map.of()));
        }
        return lines;
    }

    private boolean external(Location spawn, List<String> lines) {
        try {
            if (plugin.services() == null) {
                return false;
            }
            HologramHub hub = plugin.services().get(HologramHub.class).orElse(null);
            return hub != null && hub.show(plugin, spawn.clone().add(0, 3.5, 0), lines);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Hologram plugin was skipped: " + ex.getMessage());
            return false;
        }
    }

    private void clearExternal() {
        try {
            if (plugin.services() == null) {
                return;
            }
            plugin.services().get(HologramHub.class).ifPresent(HologramHub::clear);
        } catch (Throwable ignored) {
            // Armor stands are still removed below.
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
