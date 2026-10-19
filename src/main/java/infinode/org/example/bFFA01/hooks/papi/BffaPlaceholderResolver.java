package infinode.org.example.bFFA01.hooks.papi;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.PlaceholderResolver;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Expands BuildFFA tokens, then any downstream resolver such as PlaceholderAPI.
 */
public final class BffaPlaceholderResolver implements PlaceholderResolver {

    private final BFFA01 plugin;
    private volatile PlaceholderResolver downstream = PlaceholderResolver.identity();

    public BffaPlaceholderResolver(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void downstream(PlaceholderResolver resolver) {
        downstream = resolver == null ? PlaceholderResolver.identity() : resolver;
    }

    @Override
    public String apply(Player player, String text) {
        UUID id = player == null ? null : player.getUniqueId();
        String ours = BffaTokens.apply(new PluginStatSource(plugin), id, text);
        try {
            return downstream.apply(player, ours);
        } catch (Throwable ex) {
            return ours;
        }
    }
}
