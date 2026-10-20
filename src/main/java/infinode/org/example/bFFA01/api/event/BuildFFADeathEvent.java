package infinode.org.example.bFFA01.api.event;

import infinode.org.example.bFFA01.api.DamageCredit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * A death was recorded. {@link #getKiller()} is null for voids, falls, and disconnects
 * that had no online attacker. {@link #getKillerId()} and {@link #getBreakdown()} are
 * snapshots and do not need the killer's region thread.
 */
public final class BuildFFADeathEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final Player killer;
    private final UUID killerId;
    private final String killerName;
    private final List<DamageCredit> breakdown;

    public BuildFFADeathEvent(Player victim, Player killer) {
        this(victim, killer, killer == null ? null : killer.getUniqueId(), name(killer), List.of());
    }

    public BuildFFADeathEvent(Player victim, Player killer, UUID killerId, String killerName, List<DamageCredit> breakdown) {
        this.victim = victim;
        this.killer = killer;
        this.killerId = killerId;
        this.killerName = killerName == null ? "" : killerName;
        this.breakdown = breakdown == null ? List.of() : List.copyOf(breakdown);
    }

    public Player getVictim() {
        return victim;
    }

    public Player getKiller() {
        return killer;
    }

    public UUID getKillerId() {
        return killerId;
    }

    public String getKillerName() {
        return killerName;
    }

    /**
     * Everyone who damaged this life inside the assist window, largest share first.
     */
    public List<DamageCredit> getBreakdown() {
        return breakdown;
    }

    private static String name(Player player) {
        if (player == null) {
            return "";
        }
        try {
            String value = player.getName();
            return value == null ? "" : value;
        } catch (Throwable ex) {
            return "";
        }
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
