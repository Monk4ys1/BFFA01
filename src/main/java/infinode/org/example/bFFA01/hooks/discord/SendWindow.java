package infinode.org.example.bFFA01.hooks.discord;

import java.util.ArrayDeque;

/**
 * Sliding window used by the Discord webhook so a fight cannot flood the channel.
 */
public final class SendWindow {

    private final int limit;
    private final long windowMillis;
    private final ArrayDeque<Long> stamps = new ArrayDeque<>();

    public SendWindow(int limit, long windowMillis) {
        this.limit = Math.max(1, limit);
        this.windowMillis = Math.max(1L, windowMillis);
    }

    public synchronized boolean tryAcquire(long now) {
        long cutoff = now - windowMillis;
        while (!stamps.isEmpty() && stamps.peekFirst() <= cutoff) {
            stamps.removeFirst();
        }
        if (stamps.size() >= limit) {
            return false;
        }
        stamps.addLast(now);
        return true;
    }

    public synchronized int size() {
        return stamps.size();
    }
}
