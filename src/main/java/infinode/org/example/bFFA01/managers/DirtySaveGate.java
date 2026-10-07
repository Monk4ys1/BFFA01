package infinode.org.example.bFFA01.managers;

/**
 * Coalesces repeated save requests into one in-flight batch.
 */
public final class DirtySaveGate {

    private boolean dirty;
    private boolean scheduled;

    public synchronized boolean markAndShouldSchedule() {
        dirty = true;
        if (scheduled) {
            return false;
        }
        scheduled = true;
        return true;
    }

    public synchronized boolean takeSnapshotRequest() {
        scheduled = false;
        if (!dirty) {
            return false;
        }
        dirty = false;
        return true;
    }

    public synchronized void markDirtyAgain() {
        dirty = true;
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void clear() {
        dirty = false;
        scheduled = false;
    }
}
