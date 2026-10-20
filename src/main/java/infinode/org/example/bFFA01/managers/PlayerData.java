package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.storage.DamageEntry;
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

    long damageDealt(UUID uuid);

    long damageTaken(UUID uuid);

    long lifeDealt(UUID uuid);

    long lifeTaken(UUID uuid);

    int assists(UUID uuid);

    int streak(UUID uuid);

    void setStreak(UUID uuid, int streak);

    void addCombatDamage(UUID attacker, UUID victim, long hundredths);

    void addAssist(UUID uuid);

    void resetLife(UUID uuid);

    List<DamageEntry> topDamage(int limit);

    void refresh(UUID uuid);

    void handoff(UUID uuid);

    String storedPrefs(UUID uuid);

    void storedPrefs(UUID uuid, String prefs);

    void pullRemote(java.util.Set<UUID> online);

    void flush();

    default void close() {
    }
}
