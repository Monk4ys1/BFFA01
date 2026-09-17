package infinode.org.example.bFFA01.data;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.config.YamlFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Player statistics storage.
 *
 * <p>4.x wrote the whole {@code data.yml} to disk on every kill, death and coin
 * change, on the main thread. Here the data lives in memory, is written by a
 * timer (and on shutdown), and the actual disk write happens off the main
 * thread on a snapshot taken synchronously, so the server never blocks and the
 * snapshot can never tear.
 */
public final class DataManager {

    private static final int UUID_LENGTH = 36;

    private final BFFA01 plugin;
    private final YamlFile storage;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    private BukkitTask autosaveTask;

    public DataManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.storage = new YamlFile(plugin, "data.yml", false);
        load();
    }

    private void load() {
        FileConfiguration config = storage.config();
        int loaded = 0;
        for (String key : config.getKeys(false)) {
            if (key.length() != UUID_LENGTH) {
                continue;
            }
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Skipping malformed entry '" + key + "' in data.yml.");
                continue;
            }
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            PlayerData data = new PlayerData(uuid, section.getString("name"));
            data.kills(section.getInt("kills", 0));
            data.deaths(section.getInt("deaths", 0));
            data.coins(section.getInt("coins", 0));
            data.bestStreak(section.getInt("best-streak", 0));
            data.upgrades(section.getStringList("upgrades"));
            ConfigurationSection layout = section.getConfigurationSection("layout");
            if (layout != null) {
                for (int slot = 0; slot < PlayerData.LAYOUT_SLOTS; slot++) {
                    String material = layout.getString(String.valueOf(slot));
                    if (material != null && !"AIR".equalsIgnoreCase(material)) {
                        data.layoutSlot(slot, material);
                    }
                }
            }
            cache.put(uuid, data);
            loaded++;
        }
        plugin.getLogger().info("Loaded statistics for " + loaded + " player(s).");
    }

    /** Returns the cached data, creating an empty entry on first contact. */
    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new PlayerData(id, null));
    }

    /** Same as {@link #get(UUID)} but keeps the cached name up to date. */
    public PlayerData get(Player player) {
        PlayerData data = get(player.getUniqueId());
        if (!player.getName().equals(data.name())) {
            data.name(player.getName());
            dirty = true;
        }
        return data;
    }

    /** Marks the cache as changed so the next autosave writes it out. */
    public void markDirty() {
        dirty = true;
    }

    /** Top players by kills, highest first. Players without kills are skipped. */
    public List<PlayerData> topByKills(int limit) {
        List<PlayerData> all = new ArrayList<>(cache.values());
        all.removeIf(data -> data.kills() <= 0);
        all.sort(Comparator.<PlayerData>comparingInt(PlayerData::kills).reversed()
                .thenComparing(PlayerData::name, String.CASE_INSENSITIVE_ORDER));
        if (all.size() > limit) {
            return new ArrayList<>(all.subList(0, limit));
        }
        return all;
    }

    /** Finds a cached profile by name, ignoring case. */
    public PlayerData findByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (PlayerData data : cache.values()) {
            if (name.equalsIgnoreCase(data.name())) {
                return data;
            }
        }
        return null;
    }

    /** Every cached profile, used for tab completion. */
    public java.util.Collection<PlayerData> all() {
        return cache.values();
    }

    /** 1-based leaderboard position of a player, or 0 when they have no kills. */
    public int rankOf(UUID uuid) {
        PlayerData self = cache.get(uuid);
        if (self == null || self.kills() <= 0) {
            return 0;
        }
        int rank = 1;
        for (PlayerData other : cache.values()) {
            if (other.kills() > self.kills()) {
                rank++;
            }
        }
        return rank;
    }

    public void startAutosave(int intervalSeconds) {
        stopAutosave();
        long ticks = Math.max(20L, intervalSeconds * 20L);
        autosaveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> save(true), ticks, ticks);
    }

    public void stopAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
    }

    /**
     * Writes the cache to disk.
     *
     * @param async serialise on the calling (main) thread but write the bytes
     *              on the scheduler's async pool
     */
    public void save(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        String serialised = serialise();
        if (!async || !plugin.isEnabled()) {
            write(serialised);
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(serialised));
    }

    /** Builds the YAML document; must run on the thread that owns the cache. */
    private String serialise() {
        YamlConfiguration out = new YamlConfiguration();
        for (PlayerData data : cache.values()) {
            String root = data.uuid().toString();
            out.set(root + ".name", data.name());
            out.set(root + ".kills", data.kills());
            out.set(root + ".deaths", data.deaths());
            out.set(root + ".coins", data.coins());
            out.set(root + ".best-streak", data.bestStreak());
            if (!data.upgrades().isEmpty()) {
                out.set(root + ".upgrades", new ArrayList<>(data.upgrades()));
            }
            if (data.hasLayout()) {
                for (int slot = 0; slot < PlayerData.LAYOUT_SLOTS; slot++) {
                    String material = data.layoutSlot(slot);
                    if (material != null) {
                        out.set(root + ".layout." + slot, material);
                    }
                }
            }
        }
        return out.saveToString();
    }

    /** Atomic write so a crash mid-save cannot truncate data.yml. */
    private void write(String content) {
        File target = storage.file();
        Path targetPath = target.toPath();
        Path tempPath = targetPath.resolveSibling(target.getName() + ".tmp");
        try {
            Files.write(tempPath, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }

    /** Flushes synchronously; called from {@code onDisable}. */
    public void shutdown() {
        stopAutosave();
        dirty = true;
        save(false);
    }
}
