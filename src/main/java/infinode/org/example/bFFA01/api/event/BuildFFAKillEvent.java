package infinode.org.example.bFFA01.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A player kill was recorded. Stats and the streak are already updated.
 * The event cannot cancel the kill.
 */
public final class BuildFFAKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final Player victim;
    private final int streak;

    public BuildFFAKillEvent(Player killer, Player victim, int streak) {
        this.killer = killer;
        this.victim = victim;
        this.streak = streak;
    }

    public Player getKiller() {
        return killer;
    }

    public Player getVictim() {
        return victim;
    }

    public int getStreak() {
        return streak;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
