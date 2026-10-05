package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class DataManager {
    private final BFFA01 plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    public DataManager(BFFA01 plugin) {
        this.plugin = plugin;
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public FileConfiguration getConfig() {
        return dataConfig;
    }

    public void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getKills(UUID uuid) { return dataConfig.getInt(uuid + ".kills", 0); }
    public void addKill(UUID uuid) { dataConfig.set(uuid + ".kills", getKills(uuid) + 1); saveData(); }

    public int getDeaths(UUID uuid) { return dataConfig.getInt(uuid + ".deaths", 0); }
    public void addDeath(UUID uuid) { dataConfig.set(uuid + ".deaths", getDeaths(uuid) + 1); saveData(); }

    public int getCoins(UUID uuid) {
        return Math.max(0, dataConfig.getInt(uuid + ".coins", 0));
    }

    public void addCoins(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) {
            return;
        }
        long updated = (long) getCoins(uuid) + amount;
        if (updated > Integer.MAX_VALUE) {
            updated = Integer.MAX_VALUE;
        }
        dataConfig.set(uuid + ".coins", (int) updated);
        saveData();
    }

    public void removeCoins(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) {
            return;
        }
        long updated = (long) getCoins(uuid) - amount;
        if (updated < 0L) {
            updated = 0L;
        }
        dataConfig.set(uuid + ".coins", (int) updated);
        saveData();
    }

    public boolean hasUpgrade(UUID uuid, String upgrade) {
        List<String> upgrades = dataConfig.getStringList(uuid + ".upgrades");
        return upgrades.contains(upgrade);
    }

    public void addUpgrade(UUID uuid, String upgrade) {
        List<String> upgrades = dataConfig.getStringList(uuid + ".upgrades");
        if (!upgrades.contains(upgrade)) {
            upgrades.add(upgrade);
            dataConfig.set(uuid + ".upgrades", upgrades);
            saveData();
        }
    }

    public void saveKitLayout(UUID uuid, ItemStack[] contents) {
        for (int i = 0; i < 9; i++) {
            if (contents[i] != null && contents[i].getType() != org.bukkit.Material.AIR) {
                dataConfig.set(uuid + ".layout." + i, contents[i].getType().name());
            } else {
                dataConfig.set(uuid + ".layout." + i, "AIR");
            }
        }
        saveData();
    }

    public String getKitLayoutItem(UUID uuid, int slot) {
        return dataConfig.getString(uuid + ".layout." + slot);
    }
}
