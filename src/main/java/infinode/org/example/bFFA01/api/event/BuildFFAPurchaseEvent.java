package infinode.org.example.bFFA01.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A shop offer was paid for. The item or upgrade is already granted.
 */
public final class BuildFFAPurchaseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String offerId;
    private final int cost;
    private final boolean permanent;

    public BuildFFAPurchaseEvent(Player player, String offerId, int cost, boolean permanent) {
        this.player = player;
        this.offerId = offerId;
        this.cost = cost;
        this.permanent = permanent;
    }

    public Player getPlayer() {
        return player;
    }

    public String getOfferId() {
        return offerId;
    }

    public int getCost() {
        return cost;
    }

    public boolean isPermanent() {
        return permanent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
