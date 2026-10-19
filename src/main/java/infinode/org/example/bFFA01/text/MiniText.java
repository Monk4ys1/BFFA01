package infinode.org.example.bFFA01.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders MiniMessage and legacy ampersand text to section-sign strings.
 * One path works on Spigot, Paper, Purpur, and Folia from 1.16 clients upward.
 */
public final class MiniText {

    private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9_-]+)%");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{6}");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private MiniText() {
    }

    public static String render(String template, Map<String, String> placeholders) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String withValues = apply(template, placeholders);
        String mini = legacyCodesToMini(withValues);
        try {
            Component component = MINI.deserialize(mini);
            return LEGACY.serialize(component);
        } catch (RuntimeException ex) {
            return withValues;
        }
    }

    /**
     * Inserted values cannot open MiniMessage tags or legacy color codes.
     */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), 64));
        int limit = Math.min(value.length(), 64);
        for (int i = 0; i < limit; i++) {
            char c = value.charAt(i);
            if (c == '<' || c == '>' || c == '&' || c == '§' || c == '%' || Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static String limit(String line, int maxRaw) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        String trimmed = line;
        while (!trimmed.isEmpty() && (trimmed.length() > maxRaw || visibleLength(trimmed) > maxRaw)) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    static int visibleLength(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != '§' || i + 1 >= line.length()) {
                count++;
                continue;
            }
            char code = Character.toLowerCase(line.charAt(i + 1));
            if (code == 'x' && i + 13 < line.length()) {
                i += 13;
                continue;
            }
            i++;
        }
        return count;
    }

    private static String apply(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty() || template.indexOf('%') < 0) {
            return template;
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder(template.length());
        while (matcher.find()) {
            String value = placeholders.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : sanitize(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * A new legacy color resets formatting, matching vanilla chat. Hex uses {@code &#rrggbb}.
     */
    static String legacyCodesToMini(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char marker = text.charAt(i);
            if ((marker != '&' && marker != '§') || i + 1 >= text.length()) {
                out.append(marker);
                continue;
            }
            char next = text.charAt(i + 1);
            if ((next == '#' || next == 'x' || next == 'X') && marker == '&' && next == '#' && i + 7 < text.length()) {
                String hex = text.substring(i + 2, i + 8);
                if (HEX.matcher(hex).matches()) {
                    out.append("<reset><#").append(hex).append('>');
                    i += 7;
                    continue;
                }
            }
            if (next == 'x' || next == 'X') {
                String hex = readSectionHex(text, i);
                if (hex != null) {
                    out.append("<reset><#").append(hex).append('>');
                    i += 13;
                    continue;
                }
            }
            String tag = legacyTag(next);
            if (tag == null) {
                out.append(marker);
                continue;
            }
            if ("reset".equals(tag) || !isFormat(next)) {
                out.append("<reset>");
            }
            out.append('<').append(tag).append('>');
            i++;
        }
        return out.toString();
    }

    private static String readSectionHex(String text, int index) {
        if (index + 13 >= text.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(6);
        for (int pair = 0; pair < 6; pair++) {
            int at = index + 2 + pair * 2;
            if (text.charAt(at) != '§') {
                return null;
            }
            char digit = text.charAt(at + 1);
            if (Character.digit(digit, 16) < 0) {
                return null;
            }
            hex.append(digit);
        }
        return hex.toString();
    }

    private static boolean isFormat(char code) {
        char c = Character.toLowerCase(code);
        return c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o';
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            case 'r' -> "reset";
            default -> null;
        };
    }

    static String canonicalLegacy(String code) {
        String tag = legacyTag(code.isEmpty() ? ' ' : code.charAt(0));
        return tag == null ? "" : tag.toLowerCase(Locale.ROOT);
    }
}
