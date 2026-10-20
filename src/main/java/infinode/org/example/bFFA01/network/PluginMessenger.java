package infinode.org.example.bFFA01.network;

import infinode.org.example.bFFA01.compat.ServerScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Forwards a short text line through the proxy when a player is online.
 * The proxy has to deliver {@code bffa:net}. Redis is the path that does not need one.
 */
public final class PluginMessenger implements PluginMessageListener {

    public static final String CHANNEL = "bffa:net";

    private final Consumer<String> messages;
    private Plugin plugin;

    public PluginMessenger(Consumer<String> messages) {
        this.messages = messages;
    }

    public void enable(Plugin plugin) {
        this.plugin = plugin;
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        } catch (Throwable ignored) {
            this.plugin = null;
        }
    }

    public void send(String text) {
        Plugin current = plugin;
        if (current == null || text == null || text.isEmpty()) {
            return;
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        if (payload.length > 16_000) {
            return;
        }
        Player carrier = null;
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                carrier = player;
                break;
            }
        } catch (Throwable ignored) {
            return;
        }
        if (carrier == null) {
            return;
        }
        Player target = carrier;
        Runnable task = () -> {
            try {
                if (target.isOnline()) {
                    target.sendPluginMessage(current, CHANNEL, payload);
                }
            } catch (Throwable ignored) {
                // The proxy is not listening on this channel.
            }
        };
        if (current instanceof infinode.org.example.bFFA01.BFFA01 bffa && bffa.scheduler() != null) {
            ServerScheduler scheduler = bffa.scheduler();
            scheduler.runOnEntity(target, task);
        } else {
            task.run();
        }
    }

    public void disable() {
        Plugin current = plugin;
        plugin = null;
        if (current == null) {
            return;
        }
        try {
            Bukkit.getMessenger().unregisterIncomingPluginChannel(current, CHANNEL, this);
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(current, CHANNEL);
        } catch (Throwable ignored) {
            // The server is already stopping.
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] data) {
        if (!CHANNEL.equals(channel) || data == null || messages == null) {
            return;
        }
        messages.accept(new String(data, StandardCharsets.UTF_8));
    }
}
