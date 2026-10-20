package infinode.org.example.bFFA01.hooks.papi;

import infinode.org.example.bFFA01.BFFA01;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

/**
 * Loaded only after PlaceholderAPI is on the classpath. Do not reference this class
 * from startup code; {@link infinode.org.example.bFFA01.hooks.Integrations} loads it by name.
 */
public final class BffaExpansion extends PlaceholderExpansion {

    private final BFFA01 plugin;

    public BffaExpansion(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "bffa";
    }

    @Override
    public String getAuthor() {
        return "BFFA01";
    }

    @Override
    public String getVersion() {
        try {
            return plugin.getDescription().getVersion();
        } catch (Throwable ex) {
            return "3.7";
        }
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public List<String> getPlaceholders() {
        return BffaTokens.CATALOG;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        try {
            return BffaTokens.resolve(new PluginStatSource(plugin), player == null ? null : player.getUniqueId(), params);
        } catch (Throwable ex) {
            return null;
        }
    }
}
