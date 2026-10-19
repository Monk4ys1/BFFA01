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

    public synchronized void resetStats() {
        kills = 0;
        deaths = 0;
    }

    synchronized void loadCounts(int kills, int deaths, int coins) {
        this.kills = Math.max(0, kills);
        this.deaths = Math.max(0, deaths);
        this.coins = Math.max(0, coins);
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
