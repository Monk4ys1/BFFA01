package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.CombatManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Locale;

/** Join, quit, respawn, the void kill line and command blocking in combat. */
public final class PlayerListener implements Listener {

    private final BFFA01 plugin;

    public PlayerListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.dataManager().get(player);
        data.name(player.getName());
        plugin.dataManager().markDirty();

        String joinMessage = plugin.messages().get("join", "player", player.getName());
        event.setJoinMessage(joinMessage.isEmpty() ? null : joinMessage);

        plugin.mapManager().teleportToSpawn(player);
        plugin.scoreboardService().setup(player);
        plugin.mapManager().showBossBar(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (plugin.combatManager().isTagged(player) && plugin.settings().punishCombatLog()) {
            punishCombatLog(player);
            event.setQuitMessage(null);
        } else {
            String quitMessage = plugin.messages().get("quit", "player", player.getName());
            event.setQuitMessage(quitMessage.isEmpty() ? null : quitMessage);
        }

        plugin.combatManager().clear(player.getUniqueId());
        plugin.killstreakManager().clear(player.getUniqueId());
        plugin.specialItemState().clear(player.getUniqueId());
        plugin.buildMode().disable(player.getUniqueId());
        plugin.scoreboardService().remove(player);
        plugin.mapManager().hideBossBar(player);
        plugin.dataManager().markDirty();
    }

    /**
     * Counts a combat log as a death and hands the kill to the last attacker.
     *
     * <p>4.x banned the player for two seconds and pardoned them again half a
     * second later, purely to make a message appear on a disconnect screen the
     * player had already left. That touched the ban list on every combat log and
     * relied on {@code BanList#addBan}, whose signature changed in 1.21.
     */
    private void punishCombatLog(Player player) {
        PlayerData data = plugin.dataManager().get(player);
        data.addDeath();
        plugin.dataManager().markDirty();

        Player killer = plugin.combatManager().lastDamager(player.getUniqueId());
        if (killer != null && killer.isOnline() && !killer.equals(player)) {
            plugin.rewardKill(killer, player);
            plugin.messages().broadcast("combat-log", "player", player.getName(), "killer", killer.getName());
        }
        plugin.killstreakManager().resetStreak(player, killer);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin.mapManager().current() != null) {
            Location spawn = plugin.mapManager().current().spawn();
            if (spawn != null) {
                event.setRespawnLocation(spawn);
            }
        }
        // The inventory can only be filled once the respawn has completed.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.mapManager().teleportToSpawn(player);
            }
        });
    }

    /** Kills players who fall below the arena's death level. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || event.getFrom().getBlockY() == to.getBlockY()) {
            return;
        }
        if (to.getY() >= plugin.mapManager().deathY()) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.buildMode().isEnabled(player) || player.isDead() || player.getHealth() <= 0.0D) {
            return;
        }
        player.setHealth(0.0D);
    }

    /** Blocks commands during combat so nobody escapes with {@code /hub}. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.settings().blockCommandsInCombat()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(CombatManager.BYPASS_PERMISSION) || !plugin.combatManager().isTagged(player)) {
            return;
        }
        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        int colon = command.indexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1);
        }
        if (plugin.settings().isCommandAllowedInCombat(command)) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(player, "combat-command-blocked");
        Compat.playSound(player, "entity.villager.no", 0.7F, 1.0F);
    }
}
