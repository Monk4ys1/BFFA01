package infinode.org.example.bFFA01.api.event;

import infinode.org.example.bFFA01.api.DamageCredit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

/**
 * A player kill was recorded. Stats and the streak are already updated.
 * The event cannot cancel the kill.
 * {@link #getVictim()} is null when the victim already left, so listeners do not
 * touch an offline player from the killer's region thread. Use the snapshots instead.
 */
public final class BuildFFAKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final Player victim;
    private final int streak;
    private final UUID victimId;
    private final String victimName;
    private final double damage;
    private final List<DamageCredit> assists;

    public BuildFFAKillEvent(Player killer, Player victim, int streak) {
        this(killer, victim, streak, victim == null ? null : victim.getUniqueId(), name(victim), 0.0, List.of());
    }

    public BuildFFAKillEvent(Player killer, Player victim, int streak, UUID victimId, String victimName,
                              double damage, List<DamageCredit> assists) {
        this.killer = killer;
        this.victim = victim;
        this.streak = streak;
        this.victimId = victimId;
        this.victimName = victimName == null ? "" : victimName;
        this.damage = Double.isNaN(damage) || damage < 0 ? 0.0 : damage;
        this.assists = assists == null ? List.of() : List.copyOf(assists);
    }

    public Player getKiller() {
        return killer;
    }

    /**
     * Null when the victim is offline. Prefer {@link #getVictimId()} and {@link #getVictimName()}.
     */
    public Player getVictim() {
        return victim;
    }

    public UUID getVictimId() {
        return victimId;
    }

    public String getVictimName() {
        return victimName;
    }

    /**
     * Health the killer applied during this life, without overkill.
     */
    public double getDamage() {
        return damage;
    }

    /**
     * Other players inside the assist window who dealt at least the configured minimum.
     */
    public List<DamageCredit> getAssists() {
        return assists;
    }

    public int getStreak() {
        return streak;
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
