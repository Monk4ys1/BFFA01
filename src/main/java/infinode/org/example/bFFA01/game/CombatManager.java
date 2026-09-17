package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combat tagging: who is fighting, who hit them last and for how long that
 * counts. Drives the action-bar timer, the name colours and the combat-log
 * punishment.
 */
public final class CombatManager {

    /** Permission that exempts a player from being tagged at all. */
    public static final String BYPASS_PERMISSION = "bffa.bypass.combat";

    /** The action bar only needs a few updates per second to look smooth. */
    private static final long ACTIONBAR_INTERVAL_TICKS = 4L;

    private final BFFA01 plugin;
    private final Map<UUID, Long> tagUntil = new HashMap<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    /** Previous tick's tag state, so name colours only update on a change. */
    private final Set<UUID> previouslyTagged = new HashSet<>();
    private BukkitTask task;

    public CombatManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, ACTIONBAR_INTERVAL_TICKS, ACTIONBAR_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Tags both fighters and remembers who hit the victim last.
     *
     * @return {@code true} when the victim was newly tagged
     */
    public boolean tag(Player victim, Player damager) {
        if (victim == null || plugin.settings().combatTagSeconds() <= 0) {
            return false;
        }
        boolean wasTagged = isTagged(victim);
        long until = System.currentTimeMillis() + plugin.settings().combatTagMillis();

        if (!isExempt(victim)) {
            tagUntil.put(victim.getUniqueId(), until);
        }
        if (damager != null && !damager.equals(victim)) {
            lastDamager.put(victim.getUniqueId(), damager.getUniqueId());
            if (!isExempt(damager)) {
                tagUntil.put(damager.getUniqueId(), until);
            }
        }
        return !wasTagged;
    }

    private boolean isExempt(Player player) {
        return player.hasPermission(BYPASS_PERMISSION) || plugin.buildMode().isEnabled(player);
    }

    /**
     * A player counts as in combat while their tag is running and they are
     * below the safe zone. Spawn is always safe, which is what makes the
     * safe-zone protection consistent.
     */
    public boolean isTagged(Player player) {
        if (player == null || isExempt(player)) {
            return false;
        }
        if (plugin.mapManager().isInSafezone(player)) {
            return false;
        }
        return remainingMillis(player.getUniqueId()) > 0L;
    }

    public long remainingMillis(UUID uuid) {
        Long until = tagUntil.get(uuid);
        if (until == null) {
            return 0L;
        }
        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0L) {
            tagUntil.remove(uuid);
            lastDamager.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    /** The player who last hit this player while the tag is still running. */
    public Player lastDamager(UUID uuid) {
        if (remainingMillis(uuid) <= 0L) {
            return null;
        }
        UUID damagerId = lastDamager.get(uuid);
        return damagerId == null ? null : Bukkit.getPlayer(damagerId);
    }

    /** Drops every bit of state for a player (death, quit, respawn). */
    public void clear(UUID uuid) {
        tagUntil.remove(uuid);
        lastDamager.remove(uuid);
        previouslyTagged.remove(uuid);
        lastDamager.values().removeIf(uuid::equals);
    }

    private void tick() {
        boolean showActionBar = plugin.settings().actionBarCombatTimer();
        boolean colourNames = plugin.settings().colourNames();
        long tagMillis = Math.max(1L, plugin.settings().combatTagMillis());

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean tagged = isTagged(player);

            if (tagged && showActionBar) {
                long remaining = remainingMillis(uuid);
                double ratio = (double) remaining / (double) tagMillis;
                String bar = Text.progressBar(ratio, 20, "&c", "&8", '|');
                String message = plugin.messages().get("combat-actionbar",
                        "bar", bar,
                        "seconds", String.format("%.1f", remaining / 1000.0D));
                plugin.actionBar().send(player, message);
            }

            boolean wasTagged = previouslyTagged.contains(uuid);
            if (tagged != wasTagged) {
                if (tagged) {
                    previouslyTagged.add(uuid);
                } else {
                    previouslyTagged.remove(uuid);
                }
                if (colourNames) {
                    plugin.scoreboardService().updateCombatColour(player, tagged);
                }
            }
        }
    }
}
