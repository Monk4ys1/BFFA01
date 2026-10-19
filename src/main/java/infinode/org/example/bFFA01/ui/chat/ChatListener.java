package infinode.org.example.bFFA01.ui.chat;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.ui.Presentation;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;

/**
 * One chat shape for every message. Players cannot inject colors into it.
 */
public final class ChatListener implements Listener {

    private final BFFA01 plugin;

    public ChatListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Presentation presentation = plugin.presentation();
        if (presentation == null || !presentation.settings().chat()) {
            return;
        }
        event.setMessage(ChatStyle.message(event.getMessage()));
        String prefix = presentation.line("chat-format", Map.of("player", event.getPlayer().getName()));
        event.setFormat(ChatStyle.format(prefix));
    }
}
