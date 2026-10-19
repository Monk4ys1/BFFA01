package infinode.org.example.bFFA01.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A streak hit a reward threshold, or a streak of at least 5 was reset.
 */
public final class BuildFFAStreakEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int streak;
    private final boolean ruined;

    public BuildFFAStreakEvent(Player player, int streak, boolean ruined) {
        this.player = player;
        this.streak = streak;
        this.ruined = ruined;
    }

    public Player getPlayer() {
        return player;
    }

    public int getStreak() {
        return streak;
    }

    public boolean isRuined() {
        return ruined;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
