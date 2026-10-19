package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.config.StorageSettings;
import infinode.org.example.bFFA01.storage.KillEntry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class DataManager {

    /** One second. Repeated kills share a single write. */
    public static final long SAVE_DELAY_TICKS = 20L;

    private final PlayerData data;

    public DataManager(BFFA01 plugin) {
        this.data = open(plugin);
    }

    private static PlayerData open(BFFA01 plugin) {
        StorageSettings settings = StorageSettings.resolve(safeConfig(plugin));
        if (settings.type() != StorageSettings.Type.YAML) {
            try {
                plugin.getLogger().info("Opening " + settings.type().name().toLowerCase() + " player storage.");
                return new JdbcPlayerData(plugin, settings);
            } catch (RuntimeException | java.sql.SQLException ex) {
                plugin.getLogger().severe("SQL storage did not start (" + ex.getMessage() + "). Falling back to data.yml.");
            }
        }
        return new YamlPlayerData(plugin);
    }

    private static org.bukkit.configuration.file.FileConfiguration safeConfig(BFFA01 plugin) {
        try {
            return plugin.getConfig();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void useRunner(TaskRunner runner) {
        data.useRunner(runner);
    }

    public boolean isWritable() {
        return data.isWritable();
    }

    public FileConfiguration getConfig() {
        return data.getConfig();
    }

    public boolean saveData() {
        return data.saveData();
    }

    public int getKills(UUID uuid) {
        return data.getKills(uuid);
    }

    public void addKill(UUID uuid) {
        data.addKill(uuid);
    }

    public int getDeaths(UUID uuid) {
        return data.getDeaths(uuid);
    }

    public void addDeath(UUID uuid) {
        data.addDeath(uuid);
    }

    public int getCoins(UUID uuid) {
        return data.getCoins(uuid);
    }

    public boolean addCoins(UUID uuid, int amount) {
        return data.addCoins(uuid, amount);
    }

    public boolean removeCoins(UUID uuid, int amount) {
        return data.removeCoins(uuid, amount);
    }

    public boolean resetStats(UUID uuid) {
        return data.resetStats(uuid);
    }

    public boolean hasUpgrade(UUID uuid, String upgrade) {
        return data.hasUpgrade(uuid, upgrade);
    }

    public void addUpgrade(UUID uuid, String upgrade) {
        data.addUpgrade(uuid, upgrade);
    }

    public boolean saveKitLayout(UUID uuid, ItemStack[] contents) {
        return data.saveKitLayout(uuid, contents);
    }

    public String getKitLayoutItem(UUID uuid, int slot) {
        return data.getKitLayoutItem(uuid, slot);
    }

    public void rememberName(UUID uuid, String name) {
        data.rememberName(uuid, name);
    }

    public List<KillEntry> topKills(int limit) {
        return data.topKills(limit);
    }

    public void flush() {
        data.flush();
    }

    /**
     * Delay after a failed write: 20, 40, 80, 160, 320, then 640 ticks.
     */
    public static long backoffTicks(int consecutiveFailures) {
        int attempt = Math.max(1, consecutiveFailures);
        int shift = Math.min(attempt - 1, 5);
        return SAVE_DELAY_TICKS << shift;
    }
}
