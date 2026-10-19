package infinode.org.example.bFFA01.hooks.luck;

import org.bukkit.entity.Player;

/**
 * LuckPerms prefix first, then Vault chat. Color codes are kept. Percent signs are
 * removed so a prefix cannot start another placeholder.
 */
public final class Prefixes {

    private volatile Source luck = player -> "";
    private volatile Source vault = player -> "";

    public void luck(Source source) {
        luck = source == null ? player -> "" : source;
    }

    public void vault(Source source) {
        vault = source == null ? player -> "" : source;
    }

    public String prefix(Player player) {
        if (player == null) {
            return "";
        }
        String fromLuck = clean(read(luck, player));
        if (!fromLuck.isEmpty()) {
            return fromLuck;
        }
        return clean(read(vault, player));
    }

    public static String clean(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(prefix.length(), 64));
        for (int i = 0; i < prefix.length() && out.length() < 64; i++) {
            char c = prefix.charAt(i);
            if (c == '%' || c == '\n' || c == '\r') {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String read(Source source, Player player) {
        try {
            String value = source.prefix(player);
            return value == null ? "" : value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public interface Source {
        String prefix(Player player);
    }
}
