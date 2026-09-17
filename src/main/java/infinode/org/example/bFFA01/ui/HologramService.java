package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.Arena;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Floating leaderboard above the arena spawn.
 *
 * <p>The armour stands are tagged in their persistent data container, so
 * leftovers from a crash or a {@code /reload} are found and removed reliably.
 * 4.x matched on the custom name instead, which missed renamed stands and could
 * delete an unrelated hologram from another plugin.
 *
 * <p>The service was written in 4.x but never instantiated, so no hologram ever
 * appeared in game.
 */
public final class HologramService {

    /** Vertical distance between two hologram lines. */
    private static final double LINE_SPACING = 0.3D;

    private final BFFA01 plugin;
    private final NamespacedKey markerKey;
    private final List<ArmorStand> spawned = new ArrayList<>();
    private BukkitTask task;

    public HologramService(BFFA01 plugin) {
        this.plugin = plugin;
        this.markerKey = new NamespacedKey(plugin, "hologram-line");
    }

    public void start() {
        stop();
        removeStrays();
        if (!plugin.settings().hologramsEnabled()) {
            return;
        }
        long interval = plugin.settings().hologramRefreshSeconds() * 20L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 40L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clear();
    }

    /** Rebuilds the leaderboard above the current arena spawn. */
    public void refresh() {
        clear();
        if (!plugin.settings().hologramsEnabled()) {
            return;
        }
        Arena arena = plugin.mapManager().current();
        if (arena == null) {
            return;
        }
        Location spawn = arena.spawn();
        if (spawn == null || spawn.getWorld() == null) {
            return;
        }

        Location line = spawn.clone().add(0.0D, plugin.settings().hologramOffsetY(), 0.0D);
        spawnLine(line, "&b&lTOP KILLS");
        line.subtract(0.0D, LINE_SPACING, 0.0D);
        spawnLine(line, "&8&m                    ");
        line.subtract(0.0D, LINE_SPACING, 0.0D);

        List<PlayerData> top = plugin.dataManager().topByKills(plugin.settings().hologramEntries());
        if (top.isEmpty()) {
            spawnLine(line, "&7No kills recorded yet.");
            return;
        }
        int rank = 1;
        for (PlayerData data : top) {
            spawnLine(line, rankColour(rank) + "#" + rank + " &f" + data.name() + " &8· &b" + data.kills());
            line.subtract(0.0D, LINE_SPACING, 0.0D);
            rank++;
        }
    }

    private String rankColour(int rank) {
        return switch (rank) {
            case 1 -> "&6";
            case 2 -> "&f";
            case 3 -> "&c";
            default -> "&7";
        };
    }

    private void spawnLine(Location location, String text) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        try {
            ArmorStand stand = world.spawn(location, ArmorStand.class);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(Text.colorize(text));
            stand.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, "1");
            spawned.add(stand);
        } catch (Throwable e) {
            plugin.getLogger().warning("Could not spawn a hologram line: " + e.getMessage());
        }
    }

    /** Removes the stands this service spawned in this session. */
    public void clear() {
        for (ArmorStand stand : spawned) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        spawned.clear();
    }

    /** Removes tagged stands left behind by a crash or a server reload. */
    private void removeStrays() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClasses(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(markerKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover hologram line(s).");
        }
    }
}
