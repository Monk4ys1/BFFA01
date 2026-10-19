package infinode.org.example.bFFA01.ui.chat;

/**
 * Player chat is appended after a server-owned prefix.
 * The message cannot carry section signs, MiniMessage tags, or format tokens.
 */
public final class ChatStyle {

    public static final int LIMIT = 256;

    private ChatStyle() {
    }

    public static String message(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String withoutTags = raw.replaceAll("<[^>]{0,48}>", "");
        StringBuilder out = new StringBuilder(Math.min(withoutTags.length(), LIMIT));
        int limit = Math.min(withoutTags.length(), LIMIT);
        for (int i = 0; i < limit; i++) {
            char c = withoutTags.charAt(i);
            if (c == '&' && i + 1 < limit && isCode(withoutTags.charAt(i + 1))) {
                i++;
                continue;
            }
            if (c == '§' && i + 1 < limit) {
                if ((withoutTags.charAt(i + 1) == 'x' || withoutTags.charAt(i + 1) == 'X') && i + 13 < limit && hexAt(withoutTags, i)) {
                    i += 13;
                } else {
                    i++;
                }
                continue;
            }
            if (c == '%' || c == '<' || c == '>' || c == '&' || Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isCode(char code) {
        char c = Character.toLowerCase(code);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r' || c == '#' || c == 'x';
    }

    private static boolean hexAt(String text, int index) {
        if (index + 13 >= text.length()) {
            return false;
        }
        for (int pair = 0; pair < 6; pair++) {
            int at = index + 2 + pair * 2;
            if (text.charAt(at) != '§' || Character.digit(text.charAt(at + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    public static String format(String renderedPrefix) {
        String prefix = renderedPrefix == null ? "" : renderedPrefix.replace("%", "%%");
        return prefix + "§7%2$s";
    }
}
