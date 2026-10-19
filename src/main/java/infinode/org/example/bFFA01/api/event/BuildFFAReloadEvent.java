package infinode.org.example.bFFA01.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * {@code /bffa reload} finished reloading config, messages, and storage.
 */
public final class BuildFFAReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean storageReconnected;

    public BuildFFAReloadEvent(boolean storageReconnected) {
        this.storageReconnected = storageReconnected;
    }

    public boolean isStorageReconnected() {
        return storageReconnected;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
