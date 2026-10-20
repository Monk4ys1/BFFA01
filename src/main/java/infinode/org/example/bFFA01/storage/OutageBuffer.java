package infinode.org.example.bFFA01.storage;

import infinode.org.example.bFFA01.managers.AtomicYamlStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Durably queues counter deltas while MySQL is unreachable.
 * The file keeps one {@code buffer-id} for its whole life so a later fold applies it once.
 */
public final class OutageBuffer {

    public static final Object LOCK = new Object();

    private OutageBuffer() {
    }

    public static void merge(Path file, List<Spill> spills) throws IOException {
        if (file == null || spills == null || spills.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            AtomicYamlStore store = AtomicYamlStore.open(file);
            if (!store.isWritable()) {
                throw new IOException("Refusing to write " + file.getFileName());
            }
            YamlConfiguration yaml = store.configuration();
            if (!bufferUuid(yaml.getString("buffer-id"))) {
                yaml.set("buffer-id", UUID.randomUUID().toString());
            }
            for (Spill spill : spills) {
                if (spill == null || spill.delta() == null || spill.delta().id() == null) {
                    continue;
                }
                write(yaml, spill);
            }
            store.writeSnapshot(store.saveToString());
        }
    }

    /**
     * A lost connection, a refused socket, or a pool that cannot get a connection.
     * A lease miss is not an outage.
     */
    public static boolean unavailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql) {
                String state = sql.getSQLState();
                if (state != null && state.startsWith("08")) {
                    return true;
                }
                int code = sql.getErrorCode();
                if (code == 1042 || code == 2002 || code == 2003 || code == 2006 || code == 2013) {
                    return true;
                }
                SQLException next = sql.getNextException();
                if (next != null && next != current.getCause() && unavailable(next)) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && looksDown(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean looksDown(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("connection refused")
                || lower.contains("communications link failure")
                || lower.contains("connection is not available")
                || lower.contains("socket fail")
                || lower.contains("can not read response")
                || lower.contains("connection reset")
                || lower.contains("broken pipe")
                || lower.contains("no operations allowed after connection closed")
                || lower.contains("unable to connect")
                || lower.contains("failed to connect")
                || lower.contains("pool initialization")
                || lower.contains("server has gone away")
                || lower.contains("connection was killed")
                || lower.contains("connection closed");
    }

    private static void write(YamlConfiguration yaml, Spill spill) {
        String key = spill.delta().id().toString();
        boolean reset = spill.reset() || yaml.getBoolean(key + ".reset", false);
        if (spill.reset()) {
            yaml.set(key + ".reset", true);
            yaml.set(key + ".kills", clampInt(spill.delta().kills()));
            yaml.set(key + ".deaths", clampInt(spill.delta().deaths()));
            yaml.set(key + ".assists", clampInt(spill.delta().assists()));
            yaml.set(key + ".damage-dealt", clampLong(spill.delta().dealt()));
            yaml.set(key + ".damage-taken", clampLong(spill.delta().taken()));
            yaml.set(key + ".life-dealt", clampLong(spill.delta().lifeDealt()));
            yaml.set(key + ".life-taken", clampLong(spill.delta().lifeTaken()));
            yaml.set(key + ".coins", clampCoin((long) yaml.getInt(key + ".coins", 0) + spill.delta().coins()));
        } else if (spill.lifeReset()) {
            yaml.set(key + ".life-reset", true);
            add(yaml, key, spill.delta());
            yaml.set(key + ".life-dealt", clampLong(spill.delta().lifeDealt()));
            yaml.set(key + ".life-taken", clampLong(spill.delta().lifeTaken()));
        } else if (yaml.getBoolean(key + ".life-reset", false)) {
            add(yaml, key, spill.delta());
        } else {
            add(yaml, key, spill.delta());
        }
        if (reset) {
            yaml.set(key + ".reset", true);
        }
        PlayerRecord profile = spill.profile();
        if (profile == null) {
            return;
        }
        yaml.set(key + ".name", profile.name());
        yaml.set(key + ".streak", profile.streak());
        yaml.set(key + ".prefs", profile.prefs());
        yaml.set(key + ".upgrades", new java.util.ArrayList<>(profile.upgrades()));
        for (int slot = 0; slot < 9; slot++) {
            String material = profile.layout(slot);
            if (material != null) {
                yaml.set(key + ".layout." + slot, material);
            }
        }
    }

    private static void add(YamlConfiguration yaml, String key, CountDelta delta) {
        yaml.set(key + ".kills", clampInt((long) yaml.getInt(key + ".kills", 0) + delta.kills()));
        yaml.set(key + ".deaths", clampInt((long) yaml.getInt(key + ".deaths", 0) + delta.deaths()));
        yaml.set(key + ".coins", clampCoin((long) yaml.getInt(key + ".coins", 0) + delta.coins()));
        yaml.set(key + ".assists", clampInt((long) yaml.getInt(key + ".assists", 0) + delta.assists()));
        yaml.set(key + ".damage-dealt", clampLong(yaml.getLong(key + ".damage-dealt", 0L) + delta.dealt()));
        yaml.set(key + ".damage-taken", clampLong(yaml.getLong(key + ".damage-taken", 0L) + delta.taken()));
        yaml.set(key + ".life-dealt", clampLong(yaml.getLong(key + ".life-dealt", 0L) + delta.lifeDealt()));
        yaml.set(key + ".life-taken", clampLong(yaml.getLong(key + ".life-taken", 0L) + delta.lifeTaken()));
    }

    private static boolean bufferUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static int clampCoin(long value) {
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static int clampInt(long value) {
        if (value < 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static long clampLong(long value) {
        return Math.max(0L, value);
    }

    /**
     * One player's unsaved change. Counters are what SQL should add after an optional reset.
     */
    public static final class Spill {
        private final CountDelta delta;
        private final boolean reset;
        private final boolean lifeReset;
        private final PlayerRecord profile;

        public Spill(CountDelta delta, boolean reset, boolean lifeReset, PlayerRecord profile) {
            this.delta = delta;
            this.reset = reset;
            this.lifeReset = lifeReset;
            this.profile = profile;
        }

        public CountDelta delta() {
            return delta;
        }

        public boolean reset() {
            return reset;
        }

        public boolean lifeReset() {
            return lifeReset;
        }

        public PlayerRecord profile() {
            return profile;
        }
    }
}
