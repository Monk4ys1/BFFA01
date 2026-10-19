package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.BukkitRunners;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.storage.KillEntry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * YAML player data. A file that cannot be parsed is never overwritten.
 */
final class YamlPlayerData implements PlayerData {

    private final BFFA01 plugin;
    private final AtomicYamlStore store;
    private final DirtySaveGate gate = new DirtySaveGate();
    private final Object writeLock = new Object();
    private TaskRunner runner;
    private TaskRunner.Cancellable pending = TaskRunner.Cancellable.NONE;
    private long latestGeneration;
    private long committedGeneration;
    private int consecutiveFailures;

    YamlPlayerData(BFFA01 plugin) {
        this.plugin = plugin;
        Path dataFile = plugin.getDataFolder().toPath().resolve("data.yml");
        this.store = AtomicYamlStore.open(dataFile);
        if (store.hadLoadError()) {
            plugin.getLogger().severe("Refusing to load data.yml. Saves are disabled so the existing file is not overwritten.");
            preserveCorruptFile(dataFile);
            return;
        }
        if (!dataFile.toFile().exists()) {
            try {
                store.writeSnapshot(store.saveToString());
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not create data.yml: " + ex.getMessage());
            }
        }
    }

    @Override
    public void useRunner(TaskRunner runner) {
        this.runner = runner;
    }

    @Override
    public boolean isWritable() {
        return store.isWritable();
    }

    @Override
    public FileConfiguration getConfig() {
        return store.configuration();
    }

    @Override
    public boolean saveData() {
        if (!store.isWritable()) {
            return false;
        }
        requestSave();
        return true;
    }

    @Override
    public int getKills(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return store.configuration().getInt(uuid + ".kills", 0);
    }

    @Override
    public void addKill(UUID uuid) {
        if (!canMutate(uuid)) {
            return;
        }
        int kills = getKills(uuid);
        if (kills < Integer.MAX_VALUE) {
            store.configuration().set(uuid + ".kills", kills + 1);
            requestSave();
        }
    }

    @Override
    public int getDeaths(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return store.configuration().getInt(uuid + ".deaths", 0);
    }

    @Override
    public void addDeath(UUID uuid) {
        if (!canMutate(uuid)) {
            return;
        }
        int deaths = getDeaths(uuid);
        if (deaths < Integer.MAX_VALUE) {
            store.configuration().set(uuid + ".deaths", deaths + 1);
            requestSave();
        }
    }

    @Override
    public int getCoins(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return Math.max(0, store.configuration().getInt(uuid + ".coins", 0));
    }

    @Override
    public boolean addCoins(UUID uuid, int amount) {
        if (!canMutate(uuid) || amount <= 0) {
            return false;
        }
        long updated = (long) getCoins(uuid) + amount;
        if (updated > Integer.MAX_VALUE) {
            updated = Integer.MAX_VALUE;
        }
        store.configuration().set(uuid + ".coins", (int) updated);
        requestSave();
        return true;
    }

    @Override
    public boolean removeCoins(UUID uuid, int amount) {
        if (!canMutate(uuid) || amount <= 0) {
            return false;
        }
        long updated = (long) getCoins(uuid) - amount;
        if (updated < 0L) {
            updated = 0L;
        }
        store.configuration().set(uuid + ".coins", (int) updated);
        requestSave();
        return true;
    }

    @Override
    public boolean resetStats(UUID uuid) {
        if (!canMutate(uuid)) {
            return false;
        }
        store.configuration().set(uuid + ".kills", 0);
        store.configuration().set(uuid + ".deaths", 0);
        requestSave();
        return true;
    }

    @Override
    public boolean hasUpgrade(UUID uuid, String upgrade) {
        if (uuid == null || upgrade == null) {
            return false;
        }
        return store.configuration().getStringList(uuid + ".upgrades").contains(upgrade);
    }

    @Override
    public void addUpgrade(UUID uuid, String upgrade) {
        if (!canMutate(uuid) || upgrade == null) {
            return;
        }
        List<String> upgrades = store.configuration().getStringList(uuid + ".upgrades");
        if (!upgrades.contains(upgrade)) {
            upgrades.add(upgrade);
            store.configuration().set(uuid + ".upgrades", upgrades);
            requestSave();
        }
    }

    @Override
    public boolean saveKitLayout(UUID uuid, ItemStack[] contents) {
        if (!canMutate(uuid) || contents == null) {
            return false;
        }
        int slots = Math.min(9, contents.length);
        for (int i = 0; i < slots; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                store.configuration().set(uuid + ".layout." + i, contents[i].getType().name());
            } else {
                store.configuration().set(uuid + ".layout." + i, "AIR");
            }
        }
        for (int i = slots; i < 9; i++) {
            store.configuration().set(uuid + ".layout." + i, "AIR");
        }
        requestSave();
        return true;
    }

    @Override
    public String getKitLayoutItem(UUID uuid, int slot) {
        if (uuid == null) {
            return null;
        }
        return store.configuration().getString(uuid + ".layout." + slot);
    }

    @Override
    public void rememberName(UUID uuid, String name) {
        if (!canMutate(uuid) || name == null || name.isBlank()) {
            return;
        }
        String safe = name.length() > 16 ? name.substring(0, 16) : name;
        if (safe.equals(store.configuration().getString(uuid + ".name"))) {
            return;
        }
        store.configuration().set(uuid + ".name", safe);
        requestSave();
    }

    @Override
    public List<KillEntry> topKills(int limit) {
        List<KillEntry> entries = new ArrayList<>();
        for (String key : store.configuration().getKeys(false)) {
            if (key.length() != 36) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                int kills = store.configuration().getInt(key + ".kills", 0);
                String name = store.configuration().getString(key + ".name");
                if (name == null || name.isBlank()) {
                    name = id.toString().substring(0, 8);
                }
                entries.add(new KillEntry(id, name, kills));
            } catch (IllegalArgumentException ignored) {
                // Not a player row.
            }
        }
        entries.sort(Comparator.comparingInt(KillEntry::kills).reversed());
        if (limit >= 0 && entries.size() > limit) {
            return List.copyOf(entries.subList(0, limit));
        }
        return List.copyOf(entries);
    }

    @Override
    public void flush() {
        pending.cancel();
        pending = TaskRunner.Cancellable.NONE;
        if (!store.isWritable()) {
            return;
        }
        synchronized (writeLock) {
            if (committedGeneration == latestGeneration && !gate.isDirty()) {
                return;
            }
            String yaml = store.saveToString();
            long generation = ++latestGeneration;
            gate.clear();
            try {
                store.writeSnapshot(yaml);
                committedGeneration = generation;
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save data.yml during shutdown: " + ex.getMessage());
            }
        }
    }

    private boolean canMutate(UUID uuid) {
        return store.isWritable() && uuid != null;
    }

    private void preserveCorruptFile(Path dataFile) {
        if (!Files.isRegularFile(dataFile)) {
            return;
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Path corrupt = dataFile.resolveSibling("data.yml." + stamp + ".corrupt");
        int suffix = 1;
        while (Files.exists(corrupt)) {
            corrupt = dataFile.resolveSibling("data.yml." + stamp + "-" + suffix + ".corrupt");
            suffix++;
        }
        try {
            Files.copy(dataFile, corrupt);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not copy corrupt data.yml: " + ex.getMessage());
        }
    }

    private void requestSave() {
        requestSave(DataManager.SAVE_DELAY_TICKS);
    }

    private void requestSave(long delayTicks) {
        if (!store.isWritable()) {
            return;
        }
        if (!gate.markAndShouldSchedule()) {
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
        if (!store.isWritable()) {
            gate.clear();
            return;
        }
        if (!gate.takeSnapshotRequest()) {
            return;
        }
        final String yaml;
        try {
            yaml = store.saveToString();
        } catch (RuntimeException ex) {
            gate.markDirtyAgain();
            plugin.getLogger().warning("Could not serialize player data: " + ex.getMessage());
            return;
        }
        final long generation;
        synchronized (writeLock) {
            generation = ++latestGeneration;
        }
        tasks().runAsync(() -> writeAsync(generation, yaml));
    }

    private void writeAsync(long generation, String yaml) {
        IOException error = null;
        boolean retry = false;
        long retryDelay = DataManager.SAVE_DELAY_TICKS;
        synchronized (writeLock) {
            if (generation != latestGeneration) {
                return;
            }
            try {
                store.writeSnapshot(yaml);
                committedGeneration = generation;
                consecutiveFailures = 0;
            } catch (IOException ex) {
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
        IOException failed = error;
        long delayTicks = retryDelay;
        try {
            tasks().runSync(() -> {
                plugin.getLogger().warning("Failed to save data.yml: " + failed.getMessage());
                if (plugin.isEnabled()) {
                    requestSave(delayTicks);
                }
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Failed to save data.yml: " + failed.getMessage());
        }
    }
}
