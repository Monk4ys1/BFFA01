package infinode.org.example.bFFA01.ui.menu;

import org.bukkit.entity.Player;

/**
 * Menu actions use the same permission nodes as the commands that open them.
 */
final class MenuAccess {

    private MenuAccess() {
    }

    static boolean allowed(Player player, String action) {
        String node = node(action);
        return node == null || player.hasPermission(node);
    }

    private static String node(String action) {
        if (action.startsWith("buy:")) {
            return "bffa.shop";
        }
        if (action.startsWith("kit:")) {
            return "bffa.kit";
        }
        if (action.startsWith("vote:")) {
            return "bffa.maps";
        }
        if (action.startsWith("toggle:") || action.startsWith("cycle:")) {
            return "bffa.settings";
        }
        if (action.startsWith("open:")) {
            String rest = action.substring(5);
            int split = rest.indexOf(':');
            String id = split < 0 ? rest : rest.substring(0, split);
            return switch (id) {
                case "shop" -> "bffa.shop";
                case "kit" -> "bffa.kit";
                case "maps" -> "bffa.maps";
                case "stats" -> "bffa.stats";
                case "settings" -> "bffa.settings";
                default -> "bffa.menu";
            };
        }
        return null;
    }
}
