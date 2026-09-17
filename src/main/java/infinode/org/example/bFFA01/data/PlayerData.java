package infinode.org.example.bFFA01.data;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Everything BuildFFA persists about one player. Instances live in
 * {@link DataManager}'s cache for the whole server session; nothing here
 * touches the disk.
 */
public final class PlayerData {

    /** Number of hotbar slots a custom kit layout covers. */
    public static final int LAYOUT_SLOTS = 9;

    private final UUID uuid;
    private String name;
    private int kills;
    private int deaths;
    private int coins;
    private int bestStreak;
    private final Set<String> upgrades = new LinkedHashSet<>();
    private final String[] layout = new String[LAYOUT_SLOTS];

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name == null ? "Unknown" : name;
    }

    public void name(String name) {
        this.name = name;
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = Math.max(0, kills);
    }

    public void addKill() {
        kills++;
    }

    public int deaths() {
        return deaths;
    }

    public void deaths(int deaths) {
        this.deaths = Math.max(0, deaths);
    }

    public void addDeath() {
        deaths++;
    }

    public int coins() {
        return coins;
    }

    public void coins(int coins) {
        this.coins = Math.max(0, coins);
    }

    public void addCoins(int amount) {
        coins(coins + amount);
    }

    /**
     * Removes coins if the player can afford it.
     *
     * @return {@code true} when the coins were taken
     */
    public boolean spendCoins(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (coins < amount) {
            return false;
        }
        coins -= amount;
        return true;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public void bestStreak(int bestStreak) {
        this.bestStreak = Math.max(0, bestStreak);
    }

    /** Kills per death, counting a death-free player as their kill count. */
    public double kd() {
        return deaths == 0 ? kills : (double) kills / (double) deaths;
    }

    public Set<String> upgrades() {
        return Collections.unmodifiableSet(upgrades);
    }

    public boolean hasUpgrade(String id) {
        return id != null && upgrades.contains(id);
    }

    public boolean addUpgrade(String id) {
        return id != null && upgrades.add(id);
    }

    public void upgrades(Iterable<String> ids) {
        upgrades.clear();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.isEmpty()) {
                    upgrades.add(id);
                }
            }
        }
    }

    /** Material name saved for a hotbar slot, or {@code null} when unset. */
    public String layoutSlot(int slot) {
        if (slot < 0 || slot >= LAYOUT_SLOTS) {
            return null;
        }
        return layout[slot];
    }

    public void layoutSlot(int slot, String materialName) {
        if (slot < 0 || slot >= LAYOUT_SLOTS) {
            return;
        }
        layout[slot] = materialName == null || materialName.isEmpty() ? null : materialName;
    }

    public boolean hasLayout() {
        for (String entry : layout) {
            if (entry != null) {
                return true;
            }
        }
        return false;
    }

    public void clearLayout() {
        for (int i = 0; i < LAYOUT_SLOTS; i++) {
            layout[i] = null;
        }
    }
}
