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
        ensureSecret();
        if (settings.redis()) {
            redis = new RedisLink(settings.redisHost(), settings.redisPort(), settings.redisPassword(),
                    settings.redisChannel(), this::accept);
            redis.start();
            plugin.getLogger().info("Network Redis channel " + settings.redisChannel() + " on " + serverId() + ".");
        }
        if (settings.messenger()) {
            messenger = new PluginMessenger(text -> accept(text, true));
            messenger.enable(plugin);
        }
        if ((settings.redis() || settings.messenger()) && !settings.signed()) {
            plugin.getLogger().warning("Cluster secret is not ready. Network messages are ignored until the shared database has a secret.");
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

    public boolean signed() {
        return settings.signed();
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

    private void ensureSecret() {
        if (!settings.enabled() || plugin.getDataManager() == null) {
            return;
        }
        String secret = plugin.getDataManager().sharedSecret();
        if (secret == null || secret.isEmpty() || secret.equals(settings.secret())) {
            return;
        }
        settings = settings.withSecret(secret);
        plugin.getLogger().info("Cluster messages are signed.");
    }

    private void send(String kind, String body) {
        ensureSecret();
        if (!settings.signed()) {
            return;
        }
        long now = System.currentTimeMillis();
        String text = NetworkSeal.encode(settings.secret(), serverId(), kind, body, now);
        NetworkSeal.Frame frame = NetworkSeal.decode(settings.secret(), text, now, false);
        if (frame == null || text.length() > NetworkSeal.MAX_FRAME || !remember(frame)) {
            return;
        }
        RedisLink link = redis;
        if (link != null) {
            link.publish(text);
        }
        PluginMessenger channel = messenger;
        if (channel != null && settings.signed()) {
            channel.send(text);
        }
    }

    private void accept(String text) {
        accept(text, false);
    }

    private void accept(String text, boolean pluginChannel) {
        ensureSecret();
        NetworkSeal.Frame frame = NetworkSeal.decode(settings.secret(), text, System.currentTimeMillis(), pluginChannel);
        if (frame == null || serverId().equals(frame.id()) || !remember(frame)) {
            return;
        }
        if ("board".equals(frame.kind())) {
            pull();
        } else if ("streak".equals(frame.kind())) {
            announce(frame.body());
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

    private boolean remember(NetworkSeal.Frame frame) {
        String key = frame.id() + "|" + frame.kind() + "|" + frame.body() + "|" + frame.time();
        synchronized (recent) {
            if (recent.contains(key)) {
                return false;
            }
            recent.addLast(key);
            while (recent.size() > 256) {
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
