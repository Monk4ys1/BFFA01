package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

public class DataManager {

    /** One second. Repeated kills share a single write. */
    static final long SAVE_DELAY_TICKS = 20L;

    private final BFFA01 plugin;
    private final AtomicYamlStore store;
    private final DirtySaveGate gate = new DirtySaveGate();
    private final Object writeLock = new Object();
    private BukkitTask pendingTask;
    private long latestGeneration;
    private long committedGeneration;
    private int consecutiveFailures;

    public DataManager(BFFA01 plugin) {
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

    public boolean isWritable() {
        return store.isWritable();
    }

    public FileConfiguration getConfig() {
        return store.configuration();
    }

    public boolean saveData() {
        if (!store.isWritable()) {
            return false;
        }
        requestSave();
        return true;
    }

    public int getKills(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return store.configuration().getInt(uuid + ".kills", 0);
    }

    public void addKill(UUID uuid) {
        if (!canMutate(uuid)) {
            return;
        }
        store.configuration().set(uuid + ".kills", getKills(uuid) + 1);
        requestSave();
    }

    public int getDeaths(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return store.configuration().getInt(uuid + ".deaths", 0);
    }

    public void addDeath(UUID uuid) {
        if (!canMutate(uuid)) {
            return;
        }
        store.configuration().set(uuid + ".deaths", getDeaths(uuid) + 1);
        requestSave();
    }

    public int getCoins(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        return Math.max(0, store.configuration().getInt(uuid + ".coins", 0));
    }

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

    public boolean resetStats(UUID uuid) {
        if (!canMutate(uuid)) {
            return false;
        }
        store.configuration().set(uuid + ".kills", 0);
        store.configuration().set(uuid + ".deaths", 0);
        requestSave();
        return true;
    }

    public boolean hasUpgrade(UUID uuid, String upgrade) {
        if (uuid == null || upgrade == null) {
            return false;
        }
        List<String> upgrades = store.configuration().getStringList(uuid + ".upgrades");
        return upgrades.contains(upgrade);
    }

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

    public boolean saveKitLayout(UUID uuid, ItemStack[] contents) {
        if (!canMutate(uuid) || contents == null) {
            return false;
        }
        int slots = Math.min(9, contents.length);
        for (int i = 0; i < slots; i++) {
            if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
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

    public String getKitLayoutItem(UUID uuid, int slot) {
        if (uuid == null) {
            return null;
        }
        return store.configuration().getString(uuid + ".layout." + slot);
    }

    /**
     * Writes any unsaved snapshot before the plugin stops. In-flight async writes of an
     * older snapshot are ignored once this generation is published.
     */
    public void flush() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
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

    /**
     * Delay after a failed write: 20, 40, 80, 160, 320, then 640 ticks.
     */
    static long backoffTicks(int consecutiveFailures) {
        int attempt = Math.max(1, consecutiveFailures);
        int shift = Math.min(attempt - 1, 5);
        return SAVE_DELAY_TICKS << shift;
    }

    private void preserveCorruptFile(Path dataFile) {
        if (!Files.isRegularFile(dataFile)) {
            return;
        }
        Path corrupt = dataFile.resolveSibling("data.yml.corrupt");
        try {
            Files.copy(dataFile, corrupt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not copy corrupt data.yml: " + ex.getMessage());
        }
    }

    private void requestSave() {
        requestSave(SAVE_DELAY_TICKS);
    }

    private void requestSave(long delayTicks) {
        if (!store.isWritable()) {
            return;
        }
        if (!gate.markAndShouldSchedule()) {
            return;
        }
        pendingTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::snapshotAndScheduleWrite, delayTicks);
    }

    private void snapshotAndScheduleWrite() {
        pendingTask = null;
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeAsync(generation, yaml));
    }

    private void writeAsync(long generation, String yaml) {
        IOException error = null;
        boolean retry = false;
        long retryDelay = SAVE_DELAY_TICKS;
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
                retryDelay = backoffTicks(consecutiveFailures);
            }
        }
        if (!retry) {
            return;
        }
        gate.markDirtyAgain();
        IOException failed = error;
        long delayTicks = retryDelay;
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
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
