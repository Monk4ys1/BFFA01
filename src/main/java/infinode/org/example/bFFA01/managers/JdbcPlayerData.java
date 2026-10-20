package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.BukkitRunners;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.config.StorageSettings;
import infinode.org.example.bFFA01.network.ClusterSnapshot;
import infinode.org.example.bFFA01.network.InstanceIds;
import infinode.org.example.bFFA01.storage.CountDelta;
import infinode.org.example.bFFA01.storage.DamageEntry;
import infinode.org.example.bFFA01.storage.JdbcPlayerRepository;
import infinode.org.example.bFFA01.storage.KillEntry;
import infinode.org.example.bFFA01.storage.OutageBuffer;
import infinode.org.example.bFFA01.storage.PlayerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final ConcurrentHashMap<UUID, PlayerRecord> baseline = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> dirtyGen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> presence = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> absent = ConcurrentHashMap.newKeySet();
    private static final int BOARD_LIMIT = 100;
    private final DirtySaveGate gate = new DirtySaveGate();
    private final boolean bufferOnOutage;
    private volatile boolean offline;
    private volatile Runnable outageNotice = () -> {
    };
    private final Object writeLock = new Object();
    /** Drained while the database is down. {@code flush} and the async save both publish this list. */
    private final List<OutageBuffer.Spill> pendingSpills = new ArrayList<>();
    /** Held for the whole file write so {@code flush} returns only after those spills are on disk. */
    private final Object spillLock = new Object();
    private TaskRunner runner;
    private TaskRunner.Cancellable pending = TaskRunner.Cancellable.NONE;
    private long latestGeneration;
    private int consecutiveFailures;
    private volatile boolean closed;

    JdbcPlayerData(BFFA01 plugin, StorageSettings settings) throws SQLException {
        this(plugin, settings, settings.type() == StorageSettings.Type.SQLITE ? 1 : 5);
    }

    JdbcPlayerData(BFFA01 plugin, StorageSettings settings, int attempts) throws SQLException {
        this.plugin = plugin;
        this.bufferOnOutage = settings.type() == StorageSettings.Type.MYSQL;
        this.repository = JdbcPlayerRepository.open(settings, plugin.getDataFolder().toPath(), attempts);
        String preferred = InstanceIds.propose();
        String claimed = repository.claimInstance(preferred);
        InstanceIds.adopt(claimed);
        repository.lease(claimed, 20_000L);
        if (preferred.equals(claimed)) {
            plugin.getLogger().info("Instance id " + claimed + ".");
        } else {
            plugin.getLogger().warning("Instance id " + preferred + " is already live. This process is " + claimed + ".");
        }
        File shared = new File(plugin.getDataFolder(), "server-id.txt");
        if (shared.isFile()) {
            plugin.getLogger().warning("Ignoring server-id.txt. A copied data folder must not make two instances share an id.");
        }
        seedOnline();
        seedBoards();
    }

    private void seedBoards() {
        try {
            for (PlayerRecord record : repository.loadPage(0, BOARD_LIMIT)) {
                remember(record);
            }
            for (DamageEntry row : repository.topDamage(BOARD_LIMIT)) {
                if (row != null && row.id() != null && !cache.containsKey(row.id())) {
                    remember(repository.loadOne(row.id()));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not load leaderboard rows: " + ex.getMessage());
        }
    }

    private void remember(PlayerRecord record) {
        if (record == null || record.id() == null || cache.containsKey(record.id())) {
            return;
        }
        cache.put(record.id(), record);
        baseline.put(record.id(), record.copy());
        absent.remove(record.id());
    }

    private void seedOnline() {
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null) {
                    continue;
                }
                UUID id = player.getUniqueId();
                PlayerRecord loaded = repository.loadOne(id);
                if (loaded == null) {
                    loaded = new PlayerRecord(id);
                    String name = player.getName();
                    if (name != null) {
                        loaded.name(name);
                    }
                }
                cache.put(id, loaded);
                baseline.put(id, loaded.copy());
            }
        } catch (Throwable ignored) {
            // Unit tests have no server.
        }
    }

    private Set<UUID> onlineIds() {
        Set<UUID> ids = new HashSet<>();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null) {
                    ids.add(player.getUniqueId());
                }
            }
        } catch (Throwable ignored) {
            // Unit tests have no server.
        }
        return ids;
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
        try {
            int offset = 0;
            while (offset < 100_000) {
                List<PlayerRecord> page = repository.loadPage(offset, 100);
                if (page.isEmpty()) {
                    break;
                }
                for (PlayerRecord record : page) {
                    writeRecord(config, record);
                }
                if (page.size() < 100) {
                    break;
                }
                offset += page.size();
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Could not page player rows: " + ex.getMessage());
        }
        for (PlayerRecord record : cache.values()) {
            writeRecord(config, record.copy());
        }
        return config;
    }

    private static void writeRecord(YamlConfiguration config, PlayerRecord record) {
        if (record == null || record.id() == null) {
            return;
        }
        String id = record.id().toString();
        config.set(id + ".name", record.name());
        config.set(id + ".kills", record.kills());
        config.set(id + ".deaths", record.deaths());
        config.set(id + ".coins", record.coins());
        config.set(id + ".assists", record.assists());
        config.set(id + ".streak", record.streak());
        config.set(id + ".damage-dealt", record.damageDealt());
        config.set(id + ".damage-taken", record.damageTaken());
        config.set(id + ".upgrades", new ArrayList<>(record.upgrades()));
        for (int slot = 0; slot < 9; slot++) {
            String material = record.layout(slot);
            if (material != null) {
                config.set(id + ".layout." + slot, material);
            }
        }
    }

    @Override
    public boolean saveData() {
        requestSave(DataManager.SAVE_DELAY_TICKS);
        return true;
    }

    @Override
    public int getKills(UUID uuid) {
        PlayerRecord record = cached(uuid);
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
        PlayerRecord record = cached(uuid);
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
        PlayerRecord record = cached(uuid);
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
        PlayerRecord record = cached(uuid);
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
        PlayerRecord record = cached(uuid);
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
        PlayerRecord record = cached(uuid);
        return record == null ? 0L : record.damageDealt();
    }

    @Override
    public long damageTaken(UUID uuid) {
        PlayerRecord record = cached(uuid);
        return record == null ? 0L : record.damageTaken();
    }

    @Override
    public long lifeDealt(UUID uuid) {
        PlayerRecord record = cached(uuid);
        return record == null ? 0L : record.lifeDealt();
    }

    @Override
    public long lifeTaken(UUID uuid) {
        PlayerRecord record = cached(uuid);
        return record == null ? 0L : record.lifeTaken();
    }

    @Override
    public int assists(UUID uuid) {
        PlayerRecord record = cached(uuid);
        return record == null ? 0 : record.assists();
    }

    @Override
    public int streak(UUID uuid) {
        PlayerRecord record = cached(uuid);
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
        int token = bump(uuid);
        try {
            PlayerRecord pending = cache.get(uuid);
            if (pending != null && dirtyGen.containsKey(uuid)) {
                synchronized (writeLock) {
                    persist(List.of(pending.copy()));
                }
            }
            int claim = repository.claimPlayer(uuid);
            if (still(uuid, token)) {
                applyLoaded(uuid);
            }
            if (claim < 0 && runner != null) {
                tasks().runAsync(() -> claimLater(uuid, token));
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
        int token = bump(uuid);
        PlayerRecord record = cache.get(uuid);
        PlayerRecord copy = record == null ? null : record.copy();
        pending.cancel();
        pending = TaskRunner.Cancellable.NONE;
        Runnable work = () -> finishHandoff(uuid, token, copy);
        if (runner != null) {
            tasks().runAsync(work);
        } else {
            work.run();
        }
    }

    @Override
    public String storedPrefs(UUID uuid) {
        PlayerRecord record = cached(uuid);
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
            for (KillEntry row : repository.topKills(BOARD_LIMIT)) {
                mergeBoard(row.id(), boardName(row.id(), row.name()), row.kills(), -1L, online);
            }
            for (DamageEntry row : repository.topDamage(BOARD_LIMIT)) {
                mergeBoard(row.id(), boardName(row.id(), row.name()), -1, row.hundredths(), online);
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
        repository.close();
    }

    void onOutage(Runnable notice) {
        outageNotice = notice == null ? () -> {
        } : notice;
    }

    boolean buffering() {
        return offline;
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
        boolean enteredOutage = false;
        synchronized (writeLock) {
            presence.clear();
            latestGeneration++;
            try {
                if (offline) {
                    keep(drain());
                } else {
                    persist(snapshot);
                }
            } catch (SQLException ex) {
                if (bufferOnOutage && OutageBuffer.unavailable(ex)) {
                    enteredOutage = !offline;
                    offline = true;
                    keep(drain());
                } else {
                    plugin.getLogger().severe("Could not save the SQL store during shutdown: " + ex.getMessage());
                }
            }
            try {
                if (!offline) {
                    repository.retire();
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Could not retire this instance: " + ex.getMessage());
            }
        }
        if (enteredOutage) {
            plugin.getLogger().warning("MySQL is not reachable. Unsaved stats are in mysql-buffer.yml until it returns.");
        }
        if (enteredOutage || offline) {
            outageNotice.run();
        }
        writeBuffer();
    }

    private int bump(UUID uuid) {
        return presence.merge(uuid, 1, (current, ignored) -> current >= Integer.MAX_VALUE ? 1 : current + 1);
    }

    private boolean still(UUID uuid, int token) {
        return Integer.valueOf(token).equals(presence.get(uuid));
    }

    private void claimLater(UUID uuid, int token) {
        for (int attempt = 1; attempt <= 8; attempt++) {
            if (closed || !still(uuid, token)) {
                return;
            }
            try {
                Thread.sleep(40L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            synchronized (writeLock) {
                if (closed || !still(uuid, token)) {
                    return;
                }
                try {
                    int claim = repository.claimPlayer(uuid);
                    if (claim < 0 && attempt < 8) {
                        continue;
                    }
                    if (dirtyGen.containsKey(uuid)) {
                        return;
                    }
                    applyLoaded(uuid);
                    return;
                } catch (SQLException ex) {
                    plugin.getLogger().warning("Could not refresh " + uuid + ": " + ex.getMessage());
                    return;
                }
            }
        }
    }

    private void finishHandoff(UUID uuid, int token, PlayerRecord copy) {
        synchronized (writeLock) {
            if (closed || !still(uuid, token)) {
                return;
            }
            try {
                if (copy != null) {
                    persist(List.of(copy));
                }
                repository.release(uuid);
                dirtyGen.remove(uuid);
                presence.remove(uuid, token);
            } catch (SQLException ex) {
                plugin.getLogger().warning("Could not hand off " + uuid + ": " + ex.getMessage());
            }
        }
    }

    private void applyLoaded(UUID uuid) throws SQLException {
        if (dirtyGen.containsKey(uuid)) {
            return;
        }
        PlayerRecord loaded = repository.loadOne(uuid);
        if (loaded == null) {
            return;
        }
        cache.put(uuid, loaded);
        baseline.put(uuid, loaded.copy());
        absent.remove(uuid);
    }

    private void mergeBoard(UUID id, String name, int kills, long damage, Set<UUID> online) {
        if (id == null || (online != null && online.contains(id)) || dirtyGen.containsKey(id)) {
            return;
        }
        cache.computeIfAbsent(id, PlayerRecord::new).adoptBoard(name, kills, damage);
        baseline.computeIfAbsent(id, PlayerRecord::new).adoptBoard(name, kills, damage);
    }

    private static String boardName(UUID id, String name) {
        if (name == null || name.isBlank() || name.equals(id.toString().substring(0, 8))) {
            return null;
        }
        return name;
    }

    private PlayerRecord cached(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        PlayerRecord record = cache.get(uuid);
        if (record != null) {
            return record;
        }
        if (absent.contains(uuid)) {
            return null;
        }
        try {
            remember(repository.loadOne(uuid));
        } catch (SQLException ex) {
            return null;
        }
        record = cache.get(uuid);
        if (record == null && absent.size() < 256) {
            absent.add(uuid);
        }
        return record;
    }

    private PlayerRecord record(UUID uuid) {
        PlayerRecord existing = cache.get(uuid);
        if (existing != null) {
            return existing;
        }
        PlayerRecord loaded = cached(uuid);
        if (loaded != null) {
            return loaded;
        }
        absent.remove(uuid);
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

    @Override
    public ClusterSnapshot clusterView() {
        try {
            return repository.cluster();
        } catch (SQLException ex) {
            return ClusterSnapshot.alone(InstanceIds.current());
        }
    }

    @Override
    public ClusterSnapshot heartbeat(int players) {
        if (closed) {
            return null;
        }
        try {
            ClusterSnapshot snapshot = repository.heartbeat(Math.max(0, players));
            if (snapshot != null) {
                InstanceIds.adopt(snapshot.selfId());
                if (offline) {
                    offline = false;
                    plugin.getLogger().info("MySQL is accepting writes again.");
                }
                writeBuffer();
            }
            return snapshot;
        } catch (SQLException ex) {
            if (bufferOnOutage && OutageBuffer.unavailable(ex)) {
                offline = true;
                outageNotice.run();
            }
            plugin.getLogger().warning("Instance heartbeat failed: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Applies a buffer file once. The id is inserted in the same transaction as the counters.
     *
     * @return false when that id was already committed
     */
    public boolean absorb(org.bukkit.configuration.file.FileConfiguration config) throws SQLException {
        if (config == null) {
            return true;
        }
        String bufferId = config.getString("buffer-id", "");
        if (!bufferUuid(bufferId)) {
            throw new SQLException("buffer id is missing");
        }
        List<CountDelta> deltas = new ArrayList<>();
        List<PlayerRecord> profiles = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();
        Set<UUID> resets = new HashSet<>();
        Set<UUID> lifeResets = new HashSet<>();
        for (String key : config.getKeys(false)) {
            if (key.length() != 36) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            deltas.add(new CountDelta(id,
                    config.getInt(key + ".kills", 0),
                    config.getInt(key + ".deaths", 0),
                    config.getInt(key + ".coins", 0),
                    config.getInt(key + ".assists", 0),
                    config.getLong(key + ".damage-dealt", 0L),
                    config.getLong(key + ".damage-taken", 0L),
                    config.getLong(key + ".life-dealt", 0L),
                    config.getLong(key + ".life-taken", 0L)));
            PlayerRecord profile = new PlayerRecord(id);
            profile.name(config.getString(key + ".name", ""));
            profile.streak(config.getInt(key + ".streak", 0));
            profile.prefs(config.getString(key + ".prefs", ""));
            for (String upgrade : config.getStringList(key + ".upgrades")) {
                profile.addUpgrade(upgrade);
            }
            for (int slot = 0; slot < 9; slot++) {
                String material = config.getString(key + ".layout." + slot);
                if (material != null) {
                    profile.layout(slot, material);
                }
            }
            profiles.add(profile);
            ids.add(id);
            if (config.getBoolean(key + ".reset", false)) {
                resets.add(id);
            }
            if (config.getBoolean(key + ".life-reset", false)) {
                lifeResets.add(id);
            }
        }
        boolean applied = repository.applyBuffer(bufferId, deltas, profiles, resets, lifeResets);
        synchronized (writeLock) {
            for (UUID id : ids) {
                if (dirtyGen.containsKey(id)) {
                    continue;
                }
                PlayerRecord loaded = repository.loadOne(id);
                if (loaded == null) {
                    continue;
                }
                cache.put(id, loaded);
                baseline.put(id, loaded.copy());
            }
        }
        pullRemote(onlineIds());
        return applied;
    }

    String storedSecret(String preferred) throws SQLException {
        return repository.storedSecret(preferred);
    }

    private static boolean bufferUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean persist(List<PlayerRecord> snapshots) throws SQLException {
        boolean complete = true;
        if (snapshots == null) {
            return true;
        }
        for (PlayerRecord snapshot : snapshots) {
            if (snapshot == null || snapshot.id() == null) {
                continue;
            }
            if (!persistOne(snapshot)) {
                complete = false;
            }
        }
        return complete;
    }

    private boolean persistOne(PlayerRecord snap) throws SQLException {
        PlayerRecord base = baseline.computeIfAbsent(snap.id(), PlayerRecord::new);
        PlayerRecord before = base.copy();
        boolean reset = snap.kills() < before.kills()
                || snap.deaths() < before.deaths()
                || snap.assists() < before.assists()
                || snap.damageDealt() < before.damageDealt()
                || snap.damageTaken() < before.damageTaken();
        if (reset) {
            repository.resetCounters(snap.id());
            base.resetStats();
        }
        boolean lifeReset = !reset && (snap.lifeDealt() < before.lifeDealt() || snap.lifeTaken() < before.lifeTaken());
        int killDelta = reset ? snap.kills() : snap.kills() - before.kills();
        int deathDelta = reset ? snap.deaths() : snap.deaths() - before.deaths();
        int assistDelta = reset ? snap.assists() : snap.assists() - before.assists();
        long dealtDelta = reset ? snap.damageDealt() : snap.damageDealt() - before.damageDealt();
        long takenDelta = reset ? snap.damageTaken() : snap.damageTaken() - before.damageTaken();
        long lifeDealtDelta;
        long lifeTakenDelta;
        if (lifeReset) {
            if (!repository.clearLife(snap.id())) {
                return false;
            }
            base.clearLifeBaseline();
            lifeDealtDelta = snap.lifeDealt();
            lifeTakenDelta = snap.lifeTaken();
        } else if (reset) {
            lifeDealtDelta = snap.lifeDealt();
            lifeTakenDelta = snap.lifeTaken();
        } else {
            lifeDealtDelta = snap.lifeDealt() - before.lifeDealt();
            lifeTakenDelta = snap.lifeTaken() - before.lifeTaken();
        }
        CountDelta delta = new CountDelta(snap.id(),
                Math.max(0, killDelta),
                Math.max(0, deathDelta),
                snap.coins() - before.coins(),
                Math.max(0, assistDelta),
                Math.max(0L, dealtDelta),
                Math.max(0L, takenDelta),
                Math.max(0L, lifeDealtDelta),
                Math.max(0L, lifeTakenDelta));
        repository.applyDelta(delta);
        base.addStored(delta.kills(), delta.deaths(), delta.coins(), delta.assists(),
                delta.dealt(), delta.taken(), delta.lifeDealt(), delta.lifeTaken());
        if (!profileChanged(before, snap)) {
            return true;
        }
        if (!repository.saveProfile(snap)) {
            return false;
        }
        String[] layout = new String[9];
        for (int slot = 0; slot < 9; slot++) {
            layout[slot] = snap.layout(slot);
        }
        base.mirrorProfile(snap.name(), snap.streak(), snap.prefs(), snap.upgrades(), layout);
        return true;
    }

    private static boolean profileChanged(PlayerRecord before, PlayerRecord snap) {
        if (!before.name().equals(snap.name()) || before.streak() != snap.streak() || !before.prefs().equals(snap.prefs())) {
            return true;
        }
        if (!before.upgrades().equals(snap.upgrades())) {
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            String left = before.layout(slot) == null ? "" : before.layout(slot);
            String right = snap.layout(slot) == null ? "" : snap.layout(slot);
            if (!left.equals(right)) {
                return true;
            }
        }
        return false;
    }

    private void writeAsync(long generation, List<PlayerRecord> snapshot, Map<UUID, Integer> generations) {
        SQLException error = null;
        boolean retry = false;
        long retryDelay = DataManager.SAVE_DELAY_TICKS;
        boolean enteredOutage = false;
        synchronized (writeLock) {
            if (closed || generation != latestGeneration) {
                return;
            }
            try {
                if (offline) {
                    keep(drain());
                } else {
                    boolean complete = persist(snapshot);
                    if (generations != null && complete) {
                        for (Map.Entry<UUID, Integer> entry : generations.entrySet()) {
                            dirtyGen.remove(entry.getKey(), entry.getValue());
                        }
                    }
                    consecutiveFailures = 0;
                    retry = !complete;
                }
            } catch (SQLException ex) {
                if (bufferOnOutage && OutageBuffer.unavailable(ex)) {
                    enteredOutage = !offline;
                    offline = true;
                    keep(drain());
                } else {
                    error = ex;
                    retry = true;
                    consecutiveFailures++;
                    retryDelay = DataManager.backoffTicks(consecutiveFailures);
                }
            }
        }
        if (enteredOutage) {
            plugin.getLogger().warning("MySQL is not reachable. Unsaved stats are in mysql-buffer.yml until it returns.");
            outageNotice.run();
        }
        writeBuffer();
        if (!retry) {
            return;
        }
        gate.markDirtyAgain();
        SQLException failed = error;
        long delay = retryDelay;
        tasks().runSync(() -> {
            if (failed != null) {
                plugin.getLogger().warning("Failed to save the SQL store: " + failed.getMessage());
            }
            if (plugin.isEnabled()) {
                requestSave(delay);
            }
        });
    }

    private List<OutageBuffer.Spill> drain() {
        List<OutageBuffer.Spill> spills = new ArrayList<>();
        for (UUID id : new ArrayList<>(dirtyGen.keySet())) {
            PlayerRecord snap = cache.get(id);
            dirtyGen.remove(id);
            if (snap == null || snap.id() == null) {
                continue;
            }
            PlayerRecord before = baseline.get(id);
            if (before == null) {
                before = new PlayerRecord(id);
            }
            OutageBuffer.Spill spill = uncommitted(snap, before);
            baseline.put(id, snap.copy());
            if (spill != null) {
                spills.add(spill);
            }
        }
        return spills;
    }

    private static OutageBuffer.Spill uncommitted(PlayerRecord snap, PlayerRecord before) {
        boolean reset = snap.kills() < before.kills()
                || snap.deaths() < before.deaths()
                || snap.assists() < before.assists()
                || snap.damageDealt() < before.damageDealt()
                || snap.damageTaken() < before.damageTaken();
        boolean lifeReset = !reset && (snap.lifeDealt() < before.lifeDealt() || snap.lifeTaken() < before.lifeTaken());
        int killDelta = reset ? snap.kills() : Math.max(0, snap.kills() - before.kills());
        int deathDelta = reset ? snap.deaths() : Math.max(0, snap.deaths() - before.deaths());
        int assistDelta = reset ? snap.assists() : Math.max(0, snap.assists() - before.assists());
        long dealtDelta = reset ? snap.damageDealt() : Math.max(0L, snap.damageDealt() - before.damageDealt());
        long takenDelta = reset ? snap.damageTaken() : Math.max(0L, snap.damageTaken() - before.damageTaken());
        long lifeDealtDelta = (reset || lifeReset) ? snap.lifeDealt() : Math.max(0L, snap.lifeDealt() - before.lifeDealt());
        long lifeTakenDelta = (reset || lifeReset) ? snap.lifeTaken() : Math.max(0L, snap.lifeTaken() - before.lifeTaken());
        CountDelta delta = new CountDelta(snap.id(), killDelta, deathDelta, snap.coins() - before.coins(), assistDelta,
                dealtDelta, takenDelta, lifeDealtDelta, lifeTakenDelta);
        boolean profile = reset || profileChanged(before, snap);
        if (!delta.any() && !profile && !lifeReset) {
            return null;
        }
        return new OutageBuffer.Spill(delta, reset, lifeReset, profile ? snap.copy() : null);
    }

    private void keep(List<OutageBuffer.Spill> spills) {
        if (spills == null || spills.isEmpty()) {
            return;
        }
        pendingSpills.addAll(spills);
    }

    /**
     * Writes spills drained by this thread or by an in-flight save. The list is taken under
     * {@code writeLock} and the file is written after that lock is released.
     */
    private void writeBuffer() {
        synchronized (spillLock) {
            List<OutageBuffer.Spill> spills;
            synchronized (writeLock) {
                if (pendingSpills.isEmpty()) {
                    return;
                }
                spills = new ArrayList<>(pendingSpills);
                pendingSpills.clear();
            }
            try {
                OutageBuffer.merge(plugin.getDataFolder().toPath().resolve("mysql-buffer.yml"), spills);
            } catch (java.io.IOException ex) {
                synchronized (writeLock) {
                    pendingSpills.addAll(0, spills);
                }
                plugin.getLogger().severe("Could not write mysql-buffer.yml: " + ex.getMessage());
            }
        }
    }
}
