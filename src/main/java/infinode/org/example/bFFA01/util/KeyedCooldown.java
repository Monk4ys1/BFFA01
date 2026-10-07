package infinode.org.example.bFFA01.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key cooldown measured with a caller-supplied clock.
 * The production kit save uses {@link System#nanoTime()}.
 */
public final class KeyedCooldown {

    private final long windowMillis;
    private final ConcurrentHashMap<UUID, Long> lastAccepted = new ConcurrentHashMap<>();

    public KeyedCooldown(long windowMillis) {
        if (windowMillis <= 0L) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        this.windowMillis = windowMillis;
    }

    public long windowMillis() {
        return windowMillis;
    }

    public int size() {
        return lastAccepted.size();
    }

    /**
     * @return time the caller must still wait, in the clock's unit, or 0 when the action is allowed
     */
    public long remainingMillis(UUID key, long now) {
        purgeExpired(now);
        Long previous = lastAccepted.get(key);
        if (previous == null) {
            return 0L;
        }
        long elapsed = now - previous;
        if (elapsed >= windowMillis) {
            lastAccepted.remove(key, previous);
            return 0L;
        }
        return windowMillis - elapsed;
    }

    public void markUsed(UUID key, long now) {
        purgeExpired(now);
        lastAccepted.put(key, now);
    }

    private void purgeExpired(long now) {
        lastAccepted.entrySet().removeIf(entry -> now - entry.getValue() >= windowMillis);
    }
}
