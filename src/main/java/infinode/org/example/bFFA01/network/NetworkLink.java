package infinode.org.example.bFFA01.network;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shares leaderboard refreshes and streak broadcasts when {@code network.enabled} is true.
 */
public final class NetworkLink {

    private final BFFA01 plugin;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private volatile NetworkSettings settings = NetworkSettings.load(null);
    private RedisLink redis;
    private PluginMessenger messenger;
    private TaskRunner.Cancellable task = TaskRunner.Cancellable.NONE;
    private long lastBoard;
    private boolean warnedStorage;

    public NetworkLink(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reload();
    }

    public void reload() {
        stop();
        settings = NetworkSettings.load(plugin.getConfig());
        if (!settings.enabled()) {
            return;
        }
        if (plugin.getDataManager() != null && !"mysql".equals(plugin.getDataManager().storageLabel()) && !warnedStorage) {
            warnedStorage = true;
            plugin.getLogger().warning("network.enabled is on, but storage is not mysql. Stats stay on this server.");
        }
        if (settings.redis()) {
            redis = new RedisLink(settings.redisHost(), settings.redisPort(), settings.redisPassword(),
                    settings.redisChannel(), this::accept);
            redis.start();
            plugin.getLogger().info("Network Redis channel " + settings.redisChannel() + " on " + serverId() + ".");
        }
        if (settings.messenger()) {
            messenger = new PluginMessenger(this::accept);
            messenger.enable(plugin);
        }
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler != null) {
            long period = settings.leaderboardSeconds() * 20L;
            task = scheduler.runTimer(this::pull, period, period);
        }
    }

    public void stop() {
        task.cancel();
        task = TaskRunner.Cancellable.NONE;
        if (redis != null) {
            redis.close();
            redis = null;
        }
        if (messenger != null) {
            messenger.disable();
            messenger = null;
        }
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public String serverId() {
        return InstanceIds.current();
    }

    public boolean redisConnected() {
        RedisLink link = redis;
        return link != null && link.connected();
    }

    public void publishBoard() {
        if (!settings.enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBoard < 2_000L) {
            return;
        }
        lastBoard = now;
        send("board", "1");
    }

    public void publishStreak(String name, int streak) {
        if (!settings.enabled() || name == null || name.isBlank() || streak < 1) {
            return;
        }
        send("streak", clean(name) + "|" + streak);
    }

    private void send(String kind, String body) {
        String text = "1|" + serverId() + "|" + kind + "|" + body + "|" + System.currentTimeMillis();
        if (text.length() > 240) {
            return;
        }
        remember(text);
        RedisLink link = redis;
        if (link != null) {
            link.publish(text);
        }
        PluginMessenger channel = messenger;
        if (channel != null) {
            channel.send(text);
        }
    }

    private void accept(String text) {
        if (text == null || !text.startsWith("1|")) {
            return;
        }
        String[] parts = text.split("\\|", 5);
        if (parts.length < 5 || serverId().equals(parts[1]) || !remember(text)) {
            return;
        }
        if ("board".equals(parts[2])) {
            pull();
        } else if ("streak".equals(parts[2])) {
            announce(parts[3]);
        }
    }

    private void pull() {
        if (plugin.getDataManager() == null) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player != null) {
                    online.add(player.getUniqueId());
                }
            }
        } catch (Throwable ignored) {
            return;
        }
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> {
            try {
                plugin.getDataManager().pullRemote(online);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Network leaderboard refresh failed: " + ex.getMessage());
            }
        };
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.runAsync(task);
        }
    }

    private void announce(String body) {
        int split = body.indexOf('|');
        if (split <= 0) {
            return;
        }
        String name = clean(body.substring(0, split));
        int streak;
        try {
            streak = Integer.parseInt(body.substring(split + 1));
        } catch (NumberFormatException ex) {
            return;
        }
        String message = TextLines.render(plugin, "network-streak", "&6%player% &7is on a &e%streak% &7streak on another server.",
                Map.of("player", name, "streak", Integer.toString(streak)));
        ServerScheduler scheduler = plugin.scheduler();
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null) {
                    continue;
                }
                if (scheduler == null) {
                    player.sendMessage(message);
                } else {
                    scheduler.runOnEntity(player, () -> player.sendMessage(message));
                }
            }
        } catch (Throwable ignored) {
            // Players are still joining.
        }
    }

    private boolean remember(String text) {
        synchronized (recent) {
            if (recent.contains(text)) {
                return false;
            }
            recent.addLast(text);
            while (recent.size() > 64) {
                recent.removeFirst();
            }
            return true;
        }
    }

    private static String clean(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 16; i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                out.append(c);
            }
        }
        return out.toString();
    }
}
