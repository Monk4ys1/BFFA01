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

    private final BFFA01 plugin;
    private final Object gate = new Object();
    private PlayerData data;
    private TaskRunner runner;
    private String storageLabel = "yaml";

    public DataManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.data = open(plugin, true);
    }

    private PlayerData open(BFFA01 plugin, boolean fallbackToYaml) {
        StorageSettings settings = StorageSettings.resolve(safeConfig(plugin));
        if (settings.type() != StorageSettings.Type.YAML) {
            try {
                plugin.getLogger().info("Opening " + settings.type().name().toLowerCase() + " player storage.");
                PlayerData opened = new JdbcPlayerData(plugin, settings);
                storageLabel = settings.type().name().toLowerCase(java.util.Locale.ROOT);
                return opened;
            } catch (RuntimeException | java.sql.SQLException ex) {
                if (!fallbackToYaml) {
                    throw new IllegalStateException(ex.getMessage(), ex);
                }
                plugin.getLogger().severe("SQL storage did not start (" + ex.getMessage() + "). Falling back to data.yml.");
            }
        }
        storageLabel = "yaml";
        return new YamlPlayerData(plugin);
    }

    /**
     * Flushes the current store and opens whatever {@code storage} now says.
     * A failed open keeps the store that was already running.
     */
    public boolean reconnect() {
        synchronized (gate) {
            try {
                data.flush();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not flush storage before reconnect: " + ex.getMessage());
                return false;
            }
            PlayerData next;
            try {
                next = open(plugin, false);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Storage reconnect failed (" + ex.getMessage() + "). Keeping the current store.");
                return false;
            }
            try {
                data.close();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Could not close the previous store: " + ex.getMessage());
            }
            data = next;
            if (runner != null) {
                data.useRunner(runner);
            }
            plugin.getLogger().info("Storage reconnected (" + storageLabel + ").");
            return true;
        }
    }

    public String storageLabel() {
        synchronized (gate) {
            return storageLabel;
        }
    }

    private static org.bukkit.configuration.file.FileConfiguration safeConfig(BFFA01 plugin) {
        try {
            return plugin.getConfig();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public void useRunner(TaskRunner runner) {
        synchronized (gate) {
            this.runner = runner;
            data.useRunner(runner);
        }
    }

    public boolean isWritable() {
        synchronized (gate) {
            return data.isWritable();
        }
    }

    public FileConfiguration getConfig() {
        synchronized (gate) {
            return data.getConfig();
        }
    }

    public boolean saveData() {
        synchronized (gate) {
            return data.saveData();
        }
    }

    public int getKills(UUID uuid) {
        synchronized (gate) {
            return data.getKills(uuid);
        }
    }

    public void addKill(UUID uuid) {
        synchronized (gate) {
            data.addKill(uuid);
        }
    }

    public int getDeaths(UUID uuid) {
        synchronized (gate) {
            return data.getDeaths(uuid);
        }
    }

    public void addDeath(UUID uuid) {
        synchronized (gate) {
            data.addDeath(uuid);
        }
    }

    public int getCoins(UUID uuid) {
        synchronized (gate) {
            return data.getCoins(uuid);
        }
    }

    public boolean addCoins(UUID uuid, int amount) {
        synchronized (gate) {
            return data.addCoins(uuid, amount);
        }
    }

    public boolean removeCoins(UUID uuid, int amount) {
        synchronized (gate) {
            return data.removeCoins(uuid, amount);
        }
    }

    public boolean resetStats(UUID uuid) {
        synchronized (gate) {
            return data.resetStats(uuid);
        }
    }

    public boolean hasUpgrade(UUID uuid, String upgrade) {
        synchronized (gate) {
            return data.hasUpgrade(uuid, upgrade);
        }
    }

    public void addUpgrade(UUID uuid, String upgrade) {
        synchronized (gate) {
            data.addUpgrade(uuid, upgrade);
        }
    }

    public boolean saveKitLayout(UUID uuid, ItemStack[] contents) {
        synchronized (gate) {
            return data.saveKitLayout(uuid, contents);
        }
    }

    public String getKitLayoutItem(UUID uuid, int slot) {
        synchronized (gate) {
            return data.getKitLayoutItem(uuid, slot);
        }
    }

    public void rememberName(UUID uuid, String name) {
        synchronized (gate) {
            data.rememberName(uuid, name);
        }
    }

    public List<KillEntry> topKills(int limit) {
        synchronized (gate) {
            return data.topKills(limit);
        }
    }

    public void flush() {
        synchronized (gate) {
            data.flush();
        }
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
