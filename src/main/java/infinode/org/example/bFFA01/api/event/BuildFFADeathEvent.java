package infinode.org.example.bFFA01.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A death was recorded. {@link #getKiller()} is null for voids, falls, and disconnects
 * that had no online attacker.
 */
public final class BuildFFADeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final Player killer;

    public BuildFFADeathEvent(Player victim, Player killer) {
        this.victim = victim;
        this.killer = killer;
    }

    public Player getVictim() {
        return victim;
    }

    public Player getKiller() {
        return killer;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
