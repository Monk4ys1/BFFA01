package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.storage.KillEntry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

interface PlayerData {

    void useRunner(TaskRunner runner);

    boolean isWritable();

    FileConfiguration getConfig();

    boolean saveData();

    int getKills(UUID uuid);

    void addKill(UUID uuid);

    int getDeaths(UUID uuid);

    void addDeath(UUID uuid);

    int getCoins(UUID uuid);

    boolean addCoins(UUID uuid, int amount);

    boolean removeCoins(UUID uuid, int amount);

    boolean resetStats(UUID uuid);

    boolean hasUpgrade(UUID uuid, String upgrade);

    void addUpgrade(UUID uuid, String upgrade);

    boolean saveKitLayout(UUID uuid, ItemStack[] contents);

    String getKitLayoutItem(UUID uuid, int slot);

    void rememberName(UUID uuid, String name);

    List<KillEntry> topKills(int limit);

    void flush();
}
