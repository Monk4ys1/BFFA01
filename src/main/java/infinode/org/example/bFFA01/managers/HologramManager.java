package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.hologram.HologramHub;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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
        removeTracked();
        for (Location place : anchors()) {
            sweepAt(place, false);
        }
    }

    public void updateHologram(Location spawnLoc) {
        clearExternal();
        removeTracked();
        if (spawnLoc != null) {
            sweepAt(spawnLoc, true);
        }
        for (Location place : anchors()) {
            if (!sameColumn(place, spawnLoc)) {
                sweepAt(place, false);
            }
        }
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
        lines.add(themed("hologram-title", "&b&lTop Kills", Map.of()));
        int rank = 1;
        for (KillEntry entry : top) {
            String name = entry.name() == null || entry.name().isBlank() ? "Unknown" : entry.name();
            lines.add(themed("hologram-line", "&e#%rank% &7%player%: &b%kills%",
                    Map.of("rank", Integer.toString(rank), "player", name, "kills", Integer.toString(entry.kills()))));
            rank++;
        }
        if (top.isEmpty()) {
            lines.add(themed("hologram-empty", "&7No stats yet!", Map.of()));
        }
        return lines;
    }

    private String themed(String key, String fallback, Map<String, String> values) {
        try {
            if (plugin.presentation() != null) {
                return plugin.presentation().line(key, values);
            }
        } catch (Throwable ignored) {
            // Fall back to the message catalog.
        }
        return TextLines.render(plugin, key, fallback, values);
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

    private void removeTracked() {
        List<UUID> tracked = List.copyOf(hologramUUIDs);
        hologramUUIDs.clear();
        ServerScheduler scheduler = plugin.scheduler();
        for (UUID uuid : tracked) {
            Entity entity;
            try {
                entity = Bukkit.getEntity(uuid);
            } catch (Throwable ex) {
                continue;
            }
            if (entity == null) {
                continue;
            }
            Runnable remove = () -> {
                try {
                    entity.remove();
                } catch (Throwable ignored) {
                    // The region that owns the stand is already gone.
                }
            };
            if (scheduler != null && scheduler.folia()) {
                scheduler.runOnEntity(entity, remove);
            } else {
                remove.run();
            }
        }
    }

    /**
     * Armor-stand holograms sit a few blocks above a map spawn. Scanning a whole world from
     * the global region thread throws on Folia, so only the spawn column is touched, and on
     * Folia that touch is scheduled onto the region that owns it.
     */
    private void sweepAt(Location place, boolean inline) {
        if (place == null || place.getWorld() == null) {
            return;
        }
        Runnable job = () -> removeTaggedNear(place);
        ServerScheduler scheduler = plugin.scheduler();
        if (!inline && scheduler != null && scheduler.folia()) {
            scheduler.runAt(place, job, 0L);
            return;
        }
        try {
            job.run();
        } catch (Throwable ex) {
            if (scheduler != null && scheduler.folia()) {
                scheduler.runAt(place, job, 0L);
            } else {
                plugin.getLogger().warning("Could not sweep leftover holograms: " + ex.getMessage());
            }
        }
    }

    private void removeTaggedNear(Location place) {
        World world = place.getWorld();
        if (world == null) {
            return;
        }
        Location center = place.clone().add(0, 2, 0);
        for (Entity entity : world.getNearbyEntities(center, 6, 6, 6)) {
            if (entity instanceof ArmorStand && entity.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                entity.remove();
            }
        }
    }

    private List<Location> anchors() {
        List<Location> places = new ArrayList<>();
        ConfigurationSection maps = plugin.getConfig().getConfigurationSection("maps");
        if (maps == null) {
            return places;
        }
        for (String name : maps.getKeys(false)) {
            World world = Bukkit.getWorld(maps.getString(name + ".world", ""));
            if (world == null) {
                continue;
            }
            places.add(new Location(world, maps.getDouble(name + ".x"), maps.getDouble(name + ".y"), maps.getDouble(name + ".z")));
        }
        return places;
    }

    private static boolean sameColumn(Location left, Location right) {
        if (left == null || right == null || left.getWorld() == null || right.getWorld() == null) {
            return false;
        }
        return left.getWorld().equals(right.getWorld())
                && left.getBlockX() == right.getBlockX()
                && left.getBlockZ() == right.getBlockZ();
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
