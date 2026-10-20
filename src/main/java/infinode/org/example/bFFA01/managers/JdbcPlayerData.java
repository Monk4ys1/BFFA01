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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL-backed player rows. Reads come from memory. Writes are coalesced and asynchronous.
 */
final class JdbcPlayerData implements PlayerData {

    private final BFFA01 plugin;
    private final JdbcPlayerRepository repository;
    private final ConcurrentHashMap<UUID, PlayerRecord> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> dirtyGen = new ConcurrentHashMap<>();
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
        this.repository.lease(infinode.org.example.bFFA01.network.ServerIds.current(plugin), 30_000L);
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
            config.set(id + ".assists", copy.assists());
            config.set(id + ".streak", copy.streak());
            config.set(id + ".damage-dealt", copy.damageDealt());
            config.set(id + ".damage-taken", copy.damageTaken());
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
        touch(uuid);
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
        touch(uuid);
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
        touch(uuid);
        return true;
    }

    @Override
    public boolean removeCoins(UUID uuid, int amount) {
        if (uuid == null || !record(uuid).removeCoins(amount)) {
            return false;
        }
        touch(uuid);
        return true;
    }

    @Override
    public boolean resetStats(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        record(uuid).resetStats();
        touch(uuid);
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
            touch(uuid);
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
        touch(uuid);
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
        touch(uuid);
    }

    @Override
    public List<KillEntry> topKills(int limit) {
        try {
            return repository.topKills(limit);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not read the kill leaderboard: " + ex.getMessage());
        }
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
    public long damageDealt(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0L : record.damageDealt();
    }

    @Override
    public long damageTaken(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0L : record.damageTaken();
    }

    @Override
    public long lifeDealt(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0L : record.lifeDealt();
    }

    @Override
    public long lifeTaken(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0L : record.lifeTaken();
    }

    @Override
    public int assists(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0 : record.assists();
    }

    @Override
    public int streak(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? 0 : record.streak();
    }

    @Override
    public void setStreak(UUID uuid, int streak) {
        if (uuid == null) {
            return;
        }
        record(uuid).streak(streak);
        touch(uuid);
    }

    @Override
    public void addCombatDamage(UUID attacker, UUID victim, long hundredths) {
        if (hundredths <= 0) {
            return;
        }
        if (attacker != null) {
            record(attacker).addDealt(hundredths);
            touch(attacker);
        }
        if (victim != null) {
            record(victim).addTaken(hundredths);
            touch(victim);
        }
    }

    @Override
    public void addAssist(UUID uuid) {
        if (uuid == null) {
            return;
        }
        record(uuid).addAssist();
        touch(uuid);
    }

    @Override
    public void resetLife(UUID uuid) {
        if (uuid == null) {
            return;
        }
        record(uuid).resetLife();
        touch(uuid);
    }

    @Override
    public List<infinode.org.example.bFFA01.storage.DamageEntry> topDamage(int limit) {
        try {
            return repository.topDamage(limit);
        } catch (SQLException ex) {
            return List.of();
        }
    }

    @Override
    public void refresh(UUID uuid) {
        if (uuid == null || closed) {
            return;
        }
        try {
            PlayerRecord loaded = repository.loadOne(uuid);
            if (loaded != null) {
                cache.put(uuid, loaded);
                dirtyGen.remove(uuid);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not refresh " + uuid + ": " + ex.getMessage());
        }
    }

    @Override
    public void handoff(UUID uuid) {
        if (uuid == null || closed) {
            return;
        }
        PlayerRecord record = cache.get(uuid);
        pending.cancel();
        pending = TaskRunner.Cancellable.NONE;
        synchronized (writeLock) {
            try {
                if (record != null) {
                    repository.saveAll(List.of(record.copy()));
                }
                repository.release(uuid);
                dirtyGen.remove(uuid);
            } catch (SQLException ex) {
                plugin.getLogger().warning("Could not hand off " + uuid + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public String storedPrefs(UUID uuid) {
        PlayerRecord record = cache.get(uuid);
        return record == null ? "" : record.prefs();
    }

    @Override
    public void storedPrefs(UUID uuid, String prefs) {
        if (uuid == null) {
            return;
        }
        PlayerRecord record = record(uuid);
        String next = prefs == null ? "" : prefs;
        if (next.equals(record.prefs())) {
            return;
        }
        record.prefs(next);
        touch(uuid);
    }

    @Override
    public void pullRemote(Set<UUID> online) {
        if (closed) {
            return;
        }
        try {
            for (PlayerRecord loaded : repository.loadAll()) {
                if (online != null && online.contains(loaded.id())) {
                    continue;
                }
                if (dirtyGen.containsKey(loaded.id())) {
                    continue;
                }
                cache.put(loaded.id(), loaded);
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not refresh shared stats: " + ex.getMessage());
        }
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
        List<PlayerRecord> snapshot = snapshot(new HashMap<>());
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
        final Map<UUID, Integer> generations = new HashMap<>();
        final List<PlayerRecord> snapshot = snapshot(generations);
        final long generation;
        synchronized (writeLock) {
            generation = ++latestGeneration;
        }
        tasks().runAsync(() -> writeAsync(generation, snapshot, generations));
    }

    private void touch(UUID uuid) {
        if (uuid == null) {
            return;
        }
        dirtyGen.merge(uuid, 1, Integer::sum);
        requestSave(DataManager.SAVE_DELAY_TICKS);
    }

    private List<PlayerRecord> snapshot() {
        return snapshot(null);
    }

    private List<PlayerRecord> snapshot(Map<UUID, Integer> generations) {
        List<PlayerRecord> copies = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : dirtyGen.entrySet()) {
            PlayerRecord record = cache.get(entry.getKey());
            if (record == null) {
                continue;
            }
            if (generations != null) {
                generations.put(entry.getKey(), entry.getValue());
            }
            copies.add(record.copy());
        }
        if (copies.isEmpty() && generations == null) {
            for (PlayerRecord record : cache.values()) {
                copies.add(record.copy());
            }
        }
        return copies;
    }

    private void writeAsync(long generation, List<PlayerRecord> snapshot, Map<UUID, Integer> generations) {
        SQLException error = null;
        boolean retry = false;
        long retryDelay = DataManager.SAVE_DELAY_TICKS;
        synchronized (writeLock) {
            if (closed || generation != latestGeneration) {
                return;
            }
            try {
                repository.saveAll(snapshot);
                if (generations != null) {
                    for (Map.Entry<UUID, Integer> entry : generations.entrySet()) {
                        dirtyGen.remove(entry.getKey(), entry.getValue());
                    }
                }
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
