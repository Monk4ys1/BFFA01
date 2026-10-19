package infinode.org.example.bFFA01.hooks;

import org.bukkit.entity.Player;

/**
 * Rewrites a line before MiniMessage runs. The default leaves the line unchanged.
 * PlaceholderAPI installs a resolver that expands its tokens.
 */
public interface PlaceholderResolver {

    String apply(Player player, String text);

    static PlaceholderResolver identity() {
        return (player, text) -> text == null ? "" : text;
    }
}
