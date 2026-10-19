package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.BukkitRunners;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.config.StorageSettings;
import infinode.org.example.bFFA01.storage.JdbcPlayerRepository;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.storage.PlayerRecord;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL-backed player rows. Reads come from memory. Writes are coalesced and asynchronous.
 */
final class JdbcPlayerData implements PlayerData {

    private final BFFA01 plugin;
    private final JdbcPlayerRepository repository;
    private final ConcurrentHashMap<UUID, PlayerRecord> cache = new ConcurrentHashMap<>();
    private final DirtySaveGate gate = new DirtySaveGate();
    private final Object writeLock = new Object();
    private TaskRunner runner;
    private TaskRunner.Cancellable pending = TaskRunner.Cancellable.NONE;
    private long latestGeneration;
    private int consecutiveFailures;
    private volatile boolean closed;

    JdbcPlayerData(BFFA01 plugin, StorageSettings settings) throws SQLException {
        this.plugin = plugin;
        this.repository = JdbcPlayerRepository.open(settings, plugin.getDataFolder().toPath());
        for (PlayerRecord record : repository.loadAll()) {
            cache.put(record.id(), record);
        }
    }

    @Override
    public void useRunner(TaskRunner runner) {
        this.runner = runner;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public FileConfiguration getConfig() {
        YamlConfiguration config = new YamlConfiguration();
        for (PlayerRecord record : cache.values()) {
            PlayerRecord copy = record.copy();
            String id = copy.id().toString();
            config.set(id + ".name", copy.name());
            config.set(id + ".kills", copy.kills());
            config.set(id + ".deaths", copy.deaths());
            config.set(id + ".coins", copy.coins());
            config.set(id + ".upgrades", new ArrayList<>(copy.upgrades()));
            for (int slot = 0; slot < 9; slot++) {
                String material = copy.layout(slot);
                if (material != null) {
                    config.set(id + ".layout." + slot, material);
                }
            }
        }
        return config;
    }

    @Override
    public boolean saveData() {
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public int getKills(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0 : record.kills();
    }

    @Override
    public void addKill(UUID uuid) {
        if (uuid == null) {
            return;
        }
        record(uuid).addKill();
        requestSave(DataManager.SAVE_DELAY_TICKS);
    }

    @Override
    public int getDeaths(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0 : record.deaths();
    }

    @Override
    public void addDeath(UUID uuid) {
        if (uuid == null) {
            return;
        }
        record(uuid).addDeath();
        requestSave(DataManager.SAVE_DELAY_TICKS);
    }

    @Override
    public int getCoins(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0 : record.coins();
    }

    @Override
    public boolean addCoins(UUID uuid, int amount) {
        if (uuid == null || !record(uuid).addCoins(amount)) {
            return false;
        }
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public boolean removeCoins(UUID uuid, int amount) {
        if (uuid == null || !record(uuid).removeCoins(amount)) {
            return false;
        }
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public boolean resetStats(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        record(uuid).resetStats();
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public boolean hasUpgrade(UUID uuid, String upgrade) {
        PlayerRecord record = cache.get(uuid);
        return record != null && record.hasUpgrade(upgrade);
    }

    @Override
    public void addUpgrade(UUID uuid, String upgrade) {
        if (uuid == null) {
            return;
        }
        PlayerRecord record = record(uuid);
        if (record.hasUpgrade(upgrade)) {
            return;
        }
        record.addUpgrade(upgrade);
        if (record.hasUpgrade(upgrade)) {
            requestSave(DataManager.SAVE_DELAY_TICKS);
        }
    }

    @Override
    public boolean saveKitLayout(UUID uuid, ItemStack[] contents) {
        if (uuid == null || contents == null) {
            return false;
        }
        PlayerRecord record = record(uuid);
        int slots = Math.min(9, contents.length);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack item = contents[slot];
            if (item != null && item.getType() != Material.AIR) {
                record.layout(slot, item.getType().name());
            } else {
                record.layout(slot, "AIR");
            }
        }
        for (int slot = slots; slot < 9; slot++) {
            record.layout(slot, "AIR");
        }
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public String getKitLayoutItem(UUID uuid, int slot) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? null : record.layout(slot);
    }

    @Override
    public void rememberName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) {
            return;
        }
        String safe = name.length() > 16 ? name.substring(0, 16) : name;
        PlayerRecord record = record(uuid);
        if (safe.equals(record.name())) {
            return;
        }
        record.name(safe);
        requestSave(DataManager.SAVE_DELAY_TICKS);
    }

    @Override
    public List<KillEntry> topKills(int limit) {
        List<KillEntry> entries = new ArrayList<>();
        for (PlayerRecord record : cache.values()) {
            PlayerRecord copy = record.copy();
            String name = copy.name();
            if (name == null || name.isBlank()) {
                name = copy.id().toString().substring(0, 8);
            }
            entries.add(new KillEntry(copy.id(), name, copy.kills()));
        }
        entries.sort(Comparator.comparingInt(KillEntry::kills).reversed());
        if (limit >= 0 && entries.size() > limit) {
            return List.copyOf(entries.subList(0, limit));
        }
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.cancel();
        pending = TaskRunner.Cancellable.NONE;
        try {
            repository.close();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not close SQL storage: " + ex.getMessage());
        }
    }

    @Override
    public void flush() {
        if (closed) {
            return;
        }
        pending.cancel();
        pending = TaskRunner.Cancellable.NONE;
        gate.clear();
        List<PlayerRecord> snapshot = snapshot();
        synchronized (writeLock) {
            latestGeneration++;
            try {
                repository.saveAll(snapshot);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Could not save the SQL store during shutdown: " + ex.getMessage());
            }
        }
    }

    private PlayerRecord record(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerRecord::new);
    }

    private void requestSave(long delayTicks) {
        if (closed || !gate.markAndShouldSchedule()) {
            return;
        }
        pending = tasks().runLater(this::snapshotAndScheduleWrite, delayTicks);
    }

    private TaskRunner tasks() {
        if (runner == null) {
            runner = BukkitRunners.global(plugin);
        }
        return runner;
    }

    private void snapshotAndScheduleWrite() {
        pending = TaskRunner.Cancellable.NONE;
        if (closed || !gate.takeSnapshotRequest()) {
            return;
        }
        final List<PlayerRecord> snapshot = snapshot();
        final long generation;
        synchronized (writeLock) {
            generation = ++latestGeneration;
        }
        tasks().runAsync(() -> writeAsync(generation, snapshot));
    }

    private List<PlayerRecord> snapshot() {
        Collection<PlayerRecord> values = cache.values();
        List<PlayerRecord> copies = new ArrayList<>(values.size());
        for (PlayerRecord record : values) {
            copies.add(record.copy());
        }
        return copies;
    }

    private void writeAsync(long generation, List<PlayerRecord> snapshot) {
        SQLException error = null;
        boolean retry = false;
        long retryDelay = DataManager.SAVE_DELAY_TICKS;
        synchronized (writeLock) {
            if (closed || generation != latestGeneration) {
                return;
            }
            try {
                repository.saveAll(snapshot);
                consecutiveFailures = 0;
            } catch (SQLException ex) {
                error = ex;
                retry = true;
                consecutiveFailures++;
                retryDelay = DataManager.backoffTicks(consecutiveFailures);
            }
        }
        if (!retry) {
            return;
        }
        gate.markDirtyAgain();
        SQLException failed = error;
        long delay = retryDelay;
        tasks().runSync(() -> {
            plugin.getLogger().warning("Failed to save the SQL store: " + failed.getMessage());
            if (plugin.isEnabled()) {
                requestSave(delay);
            }
        });
    }
}
