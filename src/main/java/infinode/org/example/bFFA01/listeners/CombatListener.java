package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.data.PlayerData;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.util.Vector;

/** Damage rules, combat tagging, kills, deaths and regeneration. */
public final class CombatListener implements Listener {

    /** Strength of the knockback fireball push. */
    private static final double FIREBALL_PUSH = 3.5D;
    private static final double FIREBALL_LIFT = 1.2D;

    private final BFFA01 plugin;

    public CombatListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (plugin.settings().cancelFallDamage() && cause == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            return;
        }
        if (plugin.settings().cancelExplosionDamage()
                && (cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            event.setCancelled(true);
            return;
        }
        if (plugin.buildMode().isEnabled(player) || plugin.mapManager().isInSafezone(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player damager = resolveDamager(event);

        // A fireball only pushes, it never hurts.
        if (event.getDamager() instanceof Fireball fireball) {
            event.setDamage(0.0D);
            Vector direction = victim.getLocation().toVector().subtract(fireball.getLocation().toVector());
            if (direction.lengthSquared() > 0.0D) {
                victim.setVelocity(direction.normalize().multiply(FIREBALL_PUSH).setY(FIREBALL_LIFT));
            }
        }

        if (damager == null || damager.equals(victim) || plugin.mapManager().isInSafezone(victim)) {
            return;
        }

        // tag() marks both fighters and remembers who hit the victim last.
        if (plugin.combatManager().tag(victim, damager)) {
            plugin.messages().send(victim, "combat-tagged");
        }

        if (plugin.specialItemState().consumeVampireFang(damager.getUniqueId())) {
            Compat.heal(damager);
            Compat.playSound(damager, "entity.witch.drink", 1.0F, 0.8F);
            damager.sendMessage(plugin.messages().prefix()
                    + infinode.org.example.bFFA01.util.Text.colorize("&4Vampire Fang: &7you stole their life!"));
        }
    }

    /** Resolves the player behind a hit, following projectiles to the shooter. */
    private Player resolveDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player dead = event.getEntity();
        PlayerData deadData = plugin.dataManager().get(dead);
        deadData.addDeath();
        plugin.dataManager().markDirty();

        // Void deaths have no vanilla killer, so fall back to the combat tag.
        Player killer = dead.getKiller();
        if (killer == null) {
            killer = plugin.combatManager().lastDamager(dead.getUniqueId());
        }

        if (killer != null && !killer.equals(dead)) {
            plugin.rewardKill(killer, dead);
            event.setDeathMessage(plugin.messages().prefixed("player-killed",
                    "player", dead.getName(), "killer", killer.getName()));
        } else {
            event.setDeathMessage(plugin.messages().prefixed("player-died", "player", dead.getName()));
        }

        plugin.killstreakManager().resetStreak(dead, killer);
        plugin.combatManager().clear(dead.getUniqueId());
        plugin.specialItemState().clear(dead.getUniqueId());
        plugin.scoreboardService().updateCombatColour(dead, false);
        Compat.playSound(dead, "entity.player.hurt", 0.8F, 0.8F);

        // Skip the death screen: BuildFFA is a respawn-instantly game mode.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> Compat.respawn(dead), 3L);
    }

    /** Suppresses natural regeneration while a player is combat tagged. */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason != EntityRegainHealthEvent.RegainReason.SATIATED
                && reason != EntityRegainHealthEvent.RegainReason.REGEN) {
            return;
        }
        if (plugin.combatManager().isTagged(player)) {
            event.setCancelled(true);
        }
    }
}
