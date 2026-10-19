package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.api.event.BuildFFADeathEvent;
import infinode.org.example.bFFA01.api.event.BuildFFAKillEvent;
import infinode.org.example.bFFA01.api.event.BuildFFAMapChangeEvent;
import infinode.org.example.bFFA01.api.event.BuildFFAPurchaseEvent;
import infinode.org.example.bFFA01.api.event.BuildFFAReloadEvent;
import infinode.org.example.bFFA01.api.event.BuildFFAStreakEvent;
import infinode.org.example.bFFA01.hooks.luck.LuckPermsHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/**
 * One place gameplay code notifies integrations and other plugins.
 */
public final class MatchEvents {

    private MatchEvents() {
    }

    public static void kill(BFFA01 plugin, Player killer, Player victim) {
        String victimName = "";
        if (victim != null) {
            try {
                victimName = victim.getName();
            } catch (Throwable ignored) {
                victimName = "";
            }
        }
        kill(plugin, killer, victim, victimName);
    }

    public static void kill(BFFA01 plugin, Player killer, Player victim, String victimName) {
        if (plugin == null || killer == null) {
            return;
        }
        int streak = streak(plugin, killer);
        if (victim != null) {
            fire(plugin, new BuildFFAKillEvent(killer, victim, streak));
        }
        String killerName = "";
        try {
            killerName = killer.getName();
        } catch (Throwable ignored) {
            killerName = "";
        }
        webhook(plugin).kill(killerName == null ? "" : killerName, victimName == null ? "" : victimName, streak);
        signal(plugin, killer);
    }

    public static void death(BFFA01 plugin, Player victim, Player killer) {
        if (plugin == null || victim == null) {
            return;
        }
        fire(plugin, new BuildFFADeathEvent(victim, killer));
        webhook(plugin).death(victim.getName());
    }

    public static void streak(BFFA01 plugin, Player player, int streak, boolean ruined) {
        if (plugin == null || player == null) {
            return;
        }
        fire(plugin, new BuildFFAStreakEvent(player, streak, ruined));
        if (!ruined) {
            webhook(plugin).streak(player.getName(), streak);
        }
        signal(plugin, player);
    }

    public static void map(BFFA01 plugin, String previous, String next) {
        if (plugin == null) {
            return;
        }
        fire(plugin, new BuildFFAMapChangeEvent(previous, next));
        webhook(plugin).map(next == null ? "" : next);
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                signal(plugin, player);
            }
        } catch (Throwable ignored) {
            // The server is still starting.
        }
    }

    public static void reload(BFFA01 plugin, boolean storage) {
        fire(plugin, new BuildFFAReloadEvent(storage));
    }

    public static void purchase(BFFA01 plugin, Player player, String offer, int cost, boolean permanent) {
        fire(plugin, new BuildFFAPurchaseEvent(player, offer, cost, permanent));
    }

    public static void join(BFFA01 plugin, Player player) {
        signal(plugin, player);
    }

    private static void signal(BFFA01 plugin, Player player) {
        try {
            if (plugin.services() == null) {
                return;
            }
            plugin.services().get(LuckPermsHook.class).ifPresent(hook -> hook.signal(player));
        } catch (Throwable ignored) {
            // LuckPerms is optional.
        }
    }

    private static int streak(BFFA01 plugin, Player player) {
        try {
            return plugin.getKillstreakManager().getStreak(player);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static WebhookPublisher webhook(BFFA01 plugin) {
        try {
            if (plugin.services() == null) {
                return WebhookPublisher.noop();
            }
            return plugin.services().get(WebhookPublisher.class).orElseGet(WebhookPublisher::noop);
        } catch (Throwable ignored) {
            return WebhookPublisher.noop();
        }
    }

    private static void fire(BFFA01 plugin, Event event) {
        if (plugin == null || event == null) {
            return;
        }
        try {
            plugin.getServer().getPluginManager().callEvent(event);
        } catch (Throwable ex) {
            try {
                plugin.getLogger().warning("Could not publish " + event.getEventName() + ": " + ex.getMessage());
            } catch (Throwable ignored) {
                // Logger is not available in some tests.
            }
        }
    }
}
