package infinode.org.example.bFFA01.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers shared by every user facing string: colour translation
 * (including {@code &#RRGGBB} hex colours), chat centring, progress bars and
 * the prefix/suffix split the scoreboard needs.
 */
public final class Text {

    /** Width of the chat box in pixels, used by {@link #center(String)}. */
    private static final int CHAT_WIDTH = 154;
    /** Maximum length Bukkit accepts for a team prefix or suffix. */
    private static final int TEAM_SEGMENT_LIMIT = 64;

    private static final Pattern HEX_PATTERN = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /**
     * Translates {@code &}-codes and {@code &#RRGGBB} hex colours.
     * Hex colours require 1.16+, which every supported server satisfies.
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder out = new StringBuilder(input.length() + 16);
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
        }
        matcher.appendTail(out);
        return ChatColor.translateAlternateColorCodes('&', out.toString());
    }

    public static List<String> colorize(List<String> input) {
        List<String> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (String line : input) {
            out.add(colorize(line));
        }
        return out;
    }

    /** Expands {@code RRGGBB} into the {@code §x§R§R§G§G§B§B} form. */
    private static String toLegacyHex(String hex) {
        StringBuilder builder = new StringBuilder(14);
        builder.append(ChatColor.COLOR_CHAR).append('x');
        for (char c : hex.toCharArray()) {
            builder.append(ChatColor.COLOR_CHAR).append(Character.toLowerCase(c));
        }
        return builder.toString();
    }

    public static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(colorize(input));
    }

    /** Replaces {@code %key%} with {@code value}, ignoring a null input. */
    public static String replace(String input, String key, Object value) {
        if (input == null) {
            return "";
        }
        return input.replace("%" + key + "%", String.valueOf(value));
    }

    /**
     * Pads a message with spaces so it appears centred in the chat box.
     * Used for the headers of {@code /stats} and the admin help.
     */
    public static String center(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String colored = colorize(message);
        int messageWidth = 0;
        boolean previousWasColorChar = false;
        boolean bold = false;

        for (char c : colored.toCharArray()) {
            if (c == ChatColor.COLOR_CHAR) {
                previousWasColorChar = true;
                continue;
            }
            if (previousWasColorChar) {
                previousWasColorChar = false;
                bold = (c == 'l' || c == 'L');
                continue;
            }
            messageWidth += charWidth(c) + (bold ? 1 : 0);
        }

        int halfSpaceLeft = (CHAT_WIDTH / 2) - (messageWidth / 2);
        if (halfSpaceLeft <= 0) {
            return colored;
        }
        StringBuilder padding = new StringBuilder();
        for (int compensated = 0; compensated < halfSpaceLeft; compensated += charWidth(' ')) {
            padding.append(' ');
        }
        return padding + colored;
    }

    /** Pixel width of a character in Minecraft's default chat font. */
    private static int charWidth(char c) {
        switch (c) {
            case '!':
            case ',':
            case '.':
            case ':':
            case ';':
            case 'i':
            case '|':
            case '\'':
                return 2;
            case 'l':
            case '`':
                return 3;
            case ' ':
            case 'I':
            case '[':
            case ']':
            case 't':
                return 4;
            case '"':
            case '(':
            case ')':
            case '*':
            case '<':
            case '>':
            case 'f':
            case 'k':
            case '{':
            case '}':
                return 5;
            case '@':
            case '~':
                return 7;
            default:
                return 6;
        }
    }

    /**
     * Renders a coloured progress bar, e.g. the combat timer in the action bar.
     *
     * @param ratio        fill level between 0 and 1
     * @param length       number of symbols
     * @param filledColor  colour code for the filled part, e.g. {@code &c}
     * @param emptyColor   colour code for the empty part, e.g. {@code &7}
     * @param symbol       symbol to repeat
     */
    public static String progressBar(double ratio, int length, String filledColor, String emptyColor, char symbol) {
        int clampedLength = Math.max(1, length);
        double clampedRatio = Math.max(0.0D, Math.min(1.0D, ratio));
        int filled = (int) Math.round(clampedRatio * clampedLength);

        StringBuilder bar = new StringBuilder(filledColor);
        for (int i = 0; i < clampedLength; i++) {
            if (i == filled) {
                bar.append(emptyColor);
            }
            bar.append(symbol);
        }
        return colorize(bar.toString());
    }

    /** Formats seconds as {@code m:ss}, or {@code h:mm:ss} beyond an hour. */
    public static String formatTime(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /** Turns {@code some_map_name} into {@code Some Map Name}. */
    public static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String[] words = raw.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        return out.toString();
    }

    /**
     * Splits a scoreboard line into a team prefix and suffix. Bukkit rejects
     * segments longer than 64 characters, and a split must never separate a
     * {@code §} from the code that follows it.
     *
     * @return a two element array: {@code [prefix, suffix]}
     */
    public static String[] splitForTeam(String line) {
        String value = line == null ? "" : line;
        if (value.length() <= TEAM_SEGMENT_LIMIT) {
            return new String[]{value, ""};
        }
        int cut = TEAM_SEGMENT_LIMIT;
        // Never cut between the section sign and its code.
        if (value.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
            cut--;
        }
        String prefix = value.substring(0, cut);
        String carried = ChatColor.getLastColors(prefix);
        String suffix = carried + value.substring(cut);
        if (suffix.length() > TEAM_SEGMENT_LIMIT) {
            suffix = suffix.substring(0, TEAM_SEGMENT_LIMIT);
        }
        return new String[]{prefix, suffix};
    }
}
