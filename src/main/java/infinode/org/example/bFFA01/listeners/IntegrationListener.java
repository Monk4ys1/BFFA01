package infinode.org.example.bFFA01.listeners;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/**
 * Lets a soft dependency attach when it loads after BFFA01.
 */
public final class IntegrationListener implements Listener {

    private final BFFA01 plugin;

    public IntegrationListener(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        refresh();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        refresh();
    }

    private void refresh() {
        if (plugin.integrations() == null) {
            return;
        }
        plugin.integrations().refresh(
                name -> plugin.getServer().getPluginManager().isPluginEnabled(name),
                plugin.getLogger(),
                plugin
        );
    }
}
