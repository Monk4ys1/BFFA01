package infinode.org.example.bFFA01.storage;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory player row shared by the SQL store. Mutations are synchronized.
 */
public final class PlayerRecord {

    private final UUID id;
    private String name = "";
    private int kills;
    private int deaths;
    private int coins;
    private int assists;
    private int streak;
    private long damageDealt;
    private long damageTaken;
    private long lifeDealt;
    private long lifeTaken;
    private String prefs = "";
    private final LinkedHashSet<String> upgrades = new LinkedHashSet<>();
    private final String[] layout = new String[9];

    public PlayerRecord(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public synchronized String name() {
        return name;
    }

    public synchronized void name(String name) {
        this.name = name == null ? "" : name;
    }

    public synchronized int kills() {
        return kills;
    }

    public synchronized void addKill() {
        if (kills < Integer.MAX_VALUE) {
            kills++;
        }
    }

    public synchronized int deaths() {
        return deaths;
    }

    public synchronized void addDeath() {
        if (deaths < Integer.MAX_VALUE) {
            deaths++;
        }
    }

    public synchronized int coins() {
        return coins;
    }

    public synchronized boolean addCoins(int amount) {
        if (amount <= 0) {
            return false;
        }
        long updated = (long) coins + amount;
        coins = updated > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) updated;
        return true;
    }

    public synchronized boolean removeCoins(int amount) {
        if (amount <= 0) {
            return false;
        }
        long updated = (long) coins - amount;
        coins = updated < 0L ? 0 : (int) updated;
        return true;
    }

    public synchronized int assists() {
        return assists;
    }

    public synchronized void addAssist() {
        if (assists < Integer.MAX_VALUE) {
            assists++;
        }
    }

    public synchronized int streak() {
        return streak;
    }

    public synchronized void streak(int streak) {
        this.streak = Math.max(0, streak);
    }

    public synchronized long damageDealt() {
        return damageDealt;
    }

    public synchronized long damageTaken() {
        return damageTaken;
    }

    public synchronized long lifeDealt() {
        return lifeDealt;
    }

    public synchronized long lifeTaken() {
        return lifeTaken;
    }

    public synchronized void addDealt(long hundredths) {
        if (hundredths <= 0) {
            return;
        }
        damageDealt = saturate(damageDealt, hundredths);
        lifeDealt = saturate(lifeDealt, hundredths);
    }

    public synchronized void addTaken(long hundredths) {
        if (hundredths <= 0) {
            return;
        }
        damageTaken = saturate(damageTaken, hundredths);
        lifeTaken = saturate(lifeTaken, hundredths);
    }

    public synchronized void resetLife() {
        lifeDealt = 0;
        lifeTaken = 0;
    }

    public synchronized String prefs() {
        return prefs;
    }

    public synchronized void prefs(String prefs) {
        this.prefs = prefs == null ? "" : prefs;
    }

    public synchronized void resetStats() {
        kills = 0;
        deaths = 0;
        assists = 0;
        streak = 0;
        damageDealt = 0;
        damageTaken = 0;
        lifeDealt = 0;
        lifeTaken = 0;
    }

    public synchronized void addStored(int kills, int deaths, int coins, int assists,
                                       long dealt, long taken, long lifeDealt, long lifeTaken) {
        this.kills = clampInt(this.kills, kills);
        this.deaths = clampInt(this.deaths, deaths);
        long nextCoins = (long) this.coins + coins;
        if (nextCoins < 0L) {
            nextCoins = 0L;
        } else if (nextCoins > Integer.MAX_VALUE) {
            nextCoins = Integer.MAX_VALUE;
        }
        this.coins = (int) nextCoins;
        this.assists = clampInt(this.assists, assists);
        if (dealt > 0L) {
            this.damageDealt = saturate(this.damageDealt, dealt);
        }
        if (taken > 0L) {
            this.damageTaken = saturate(this.damageTaken, taken);
        }
        if (lifeDealt > 0L) {
            this.lifeDealt = saturate(this.lifeDealt, lifeDealt);
        }
        if (lifeTaken > 0L) {
            this.lifeTaken = saturate(this.lifeTaken, lifeTaken);
        }
    }

    public synchronized void clearLifeBaseline() {
        lifeDealt = 0L;
        lifeTaken = 0L;
    }

    public synchronized void mirrorProfile(String name, int streak, String prefs, Set<String> upgrades, String[] layout) {
        this.name = name == null ? "" : name;
        this.streak = Math.max(0, streak);
        this.prefs = prefs == null ? "" : prefs;
        this.upgrades.clear();
        if (upgrades != null) {
            this.upgrades.addAll(upgrades);
        }
        if (layout != null) {
            System.arraycopy(layout, 0, this.layout, 0, Math.min(layout.length, this.layout.length));
        }
    }

    private static int clampInt(int current, int delta) {
        long updated = (long) current + delta;
        if (updated < 0L) {
            return 0;
        }
        if (updated > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) updated;
    }

    synchronized void loadCounts(int kills, int deaths, int coins) {
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.coins = Math.max(0, coins);
    }

    synchronized void loadCombat(long damageDealt, long damageTaken, long lifeDealt, long lifeTaken, int assists, int streak) {
        this.damageDealt = Math.max(0L, damageDealt);
        this.damageTaken = Math.max(0L, damageTaken);
        this.lifeDealt = Math.max(0L, lifeDealt);
        this.lifeTaken = Math.max(0L, lifeTaken);
        this.assists = Math.max(0, assists);
        this.streak = Math.max(0, streak);
    }

    private static long saturate(long current, long add) {
        long updated = current + add;
        return updated < current ? Long.MAX_VALUE : updated;
    }

    public synchronized boolean hasUpgrade(String upgrade) {
        return upgrade != null && upgrades.contains(upgrade);
    }

    public synchronized void addUpgrade(String upgrade) {
        if (upgrade != null && upgrade.matches("[a-z0-9_]{1,32}")) {
            upgrades.add(upgrade);
        }
    }

    public synchronized String layout(int slot) {
        if (slot < 0 || slot >= layout.length) {
            return null;
        }
        return layout[slot];
    }

    public synchronized void layout(int slot, String material) {
        if (slot < 0 || slot >= layout.length) {
            return;
        }
        layout[slot] = material;
    }

    public synchronized PlayerRecord copy() {
        PlayerRecord copy = new PlayerRecord(id);
        copy.name = name;
        copy.kills = kills;
        copy.deaths = deaths;
        copy.coins = coins;
        copy.assists = assists;
        copy.streak = streak;
        copy.damageDealt = damageDealt;
        copy.damageTaken = damageTaken;
        copy.lifeDealt = lifeDealt;
        copy.lifeTaken = lifeTaken;
        copy.prefs = prefs;
        copy.upgrades.addAll(upgrades);
        System.arraycopy(layout, 0, copy.layout, 0, layout.length);
        return copy;
    }

    public synchronized Set<String> upgrades() {
        return Set.copyOf(upgrades);
    }

    static String joinUpgrades(Set<String> upgrades) {
        return String.join(",", upgrades);
    }

    static void readUpgrades(PlayerRecord record, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            record.addUpgrade(part.trim());
        }
    }

    static String joinLayout(PlayerRecord record) {
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < 9; slot++) {
            String material = record.layout(slot);
            if (material == null || material.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(slot).append('=').append(material);
        }
        return out.toString();
    }

    static void readLayout(PlayerRecord record, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int slot = Integer.parseInt(part.substring(0, eq));
                record.layout(slot, part.substring(eq + 1));
            } catch (NumberFormatException ignored) {
                // Skip a corrupt slot.
            }
        }
    }
}
