package infinode.org.example.bFFA01.compat;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Sends action-bar messages.
 *
 * <p>Bukkit itself has no action-bar API, so this goes through the BungeeCord
 * chat shim that Spigot and Paper both ship. Should a future server drop that
 * shim, the first failure disables the feature instead of spamming the console
 * every few ticks.
 */
public final class ActionBar {

    private final Plugin plugin;
    private boolean supported = true;

    public ActionBar(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Sends a legacy-coloured message, doing nothing for an empty message. */
    public void send(Player player, String message) {
        if (!supported || player == null || message == null || message.isEmpty()) {
            return;
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Throwable t) {
            supported = false;
            plugin.getLogger().warning("Action bar messages are not supported on this server ("
                    + t.getClass().getSimpleName() + "); disabling them.");
        }
    }

    public boolean isSupported() {
        return supported;
    }
}
