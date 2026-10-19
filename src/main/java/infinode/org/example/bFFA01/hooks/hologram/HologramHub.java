package infinode.org.example.bFFA01.hooks.hologram;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import org.bukkit.Location;

import java.util.List;

/**
 * Routes the leaderboard to the configured hologram plugin, or reports that the
 * armor-stand fallback should be used.
 */
public final class HologramHub {

    private volatile HologramBackend decent = HologramBackend.NONE;
    private volatile HologramBackend fancy = HologramBackend.NONE;

    public void decent(HologramBackend backend) {
        decent = backend == null ? HologramBackend.NONE : backend;
    }

    public void fancy(HologramBackend backend) {
        fancy = backend == null ? HologramBackend.NONE : backend;
    }

    public boolean show(BFFA01 plugin, Location location, List<String> lines) {
        String choice = HologramChoice.choose(
                IntegrationSettings.hologramBackend(IntegrationSettings.config(plugin)),
                decent.available(),
                fancy.available());
        if ("decentholograms".equals(choice)) {
            fancy.clear();
            return decent.show(plugin, location, lines);
        }
        if ("fancyholograms".equals(choice)) {
            decent.clear();
            return fancy.show(plugin, location, lines);
        }
        clear();
        return false;
    }

    public void clear() {
        try {
            decent.clear();
        } catch (Throwable ignored) {
            // The plugin may already be gone.
        }
        try {
            fancy.clear();
        } catch (Throwable ignored) {
            // The plugin may already be gone.
        }
    }
}
