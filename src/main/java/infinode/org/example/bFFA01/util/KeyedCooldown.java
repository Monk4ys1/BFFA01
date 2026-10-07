package infinode.org.example.bFFA01.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key cooldown measured with a caller-supplied clock.
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

    /**
     * @return milliseconds the caller must still wait, or 0 when the action is allowed
     */
    public long remainingMillis(UUID key, long now) {
        Long previous = lastAccepted.get(key);
        if (previous == null) {
            return 0L;
        }
        long elapsed = now - previous;
        if (elapsed >= windowMillis) {
            return 0L;
        }
        return windowMillis - elapsed;
    }

    public void markUsed(UUID key, long now) {
        lastAccepted.put(key, now);
    }
}
