package infinode.org.example.bFFA01.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * The current arena changed. {@link #getPreviousMap()} is null on the first selection.
 */
public final class BuildFFAMapChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String previousMap;
    private final String map;

    public BuildFFAMapChangeEvent(String previousMap, String map) {
        this.previousMap = previousMap;
        this.map = map;
    }

    public String getPreviousMap() {
        return previousMap;
    }

    public String getMap() {
        return map;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
