package infinode.org.example.bFFA01.ui.scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Fits a legacy scoreboard line into the 64-character team prefix and suffix.
 * Hex colors stay intact, and a long gradient is resampled so the words still fit.
 */
public final class SidebarFit {

    public static final int PREFIX_CAP = 64;
    public static final int SUFFIX_CAP = 64;
    private static final String CODES = "0123456789abcdef";

    private SidebarFit() {
    }

    public record Pieces(String entry, String prefix, String suffix) {
    }

    public static String entry(int line) {
        int safe = Math.floorMod(line, CODES.length() * CODES.length());
        String token = "§" + CODES.charAt(safe % CODES.length());
        int high = safe / CODES.length();
        if (high > 0) {
            token = token + "§" + CODES.charAt(high);
        }
        return token;
    }

    public static Pieces fit(String legacy, int line) {
        String fitted = compress(legacy, PREFIX_CAP + SUFFIX_CAP);
        String[] parts = split(fitted, PREFIX_CAP, SUFFIX_CAP);
        return new Pieces(entry(line), parts[0], parts[1]);
    }

    public static String compress(String legacy, int budget) {
        if (legacy == null || legacy.isEmpty() || budget <= 0) {
            return "";
        }
        if (legacy.length() <= budget) {
            return legacy;
        }
        List<Glyph> glyphs = parse(legacy);
        if (glyphs.isEmpty()) {
            return "";
        }
        int visible = glyphs.size();
        int styleCost = 2;
        for (Glyph glyph : glyphs) {
            styleCost = Math.max(styleCost, glyph.style.length());
        }
        if (visible > budget) {
            return plain(glyphs, budget);
        }
        int step = 1;
        while (step < visible) {
            int codes = (visible + step - 1) / step;
            if ((long) codes * styleCost + visible <= budget) {
                break;
            }
            step++;
        }
        int codes = (visible + step - 1) / step;
        if ((long) codes * styleCost + visible > budget) {
            return plain(glyphs, budget);
        }
        return render(glyphs, step);
    }

    static String[] split(String text, int prefixCap, int suffixCap) {
        if (text == null || text.isEmpty()) {
            return new String[]{"", ""};
        }
        int cut = boundary(text, prefixCap);
        String prefix = text.substring(0, cut);
        String rest = text.substring(cut);
        if (!rest.isEmpty() && rest.charAt(0) != '§') {
            String carry = activeStyle(text, cut);
            if (!carry.isEmpty()) {
                rest = carry + rest;
            }
        }
        if (rest.length() > suffixCap) {
            rest = compress(rest, suffixCap);
        }
        return new String[]{prefix, rest};
    }

    private static String render(List<Glyph> glyphs, int step) {
        StringBuilder out = new StringBuilder(glyphs.size() * 4);
        String previous = null;
        int stride = Math.max(1, step);
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            if (i % stride == 0 && !glyph.style.equals(previous)) {
                out.append(glyph.style);
                previous = glyph.style;
            }
            out.append(glyph.ch);
        }
        return out.toString();
    }

    private static String plain(List<Glyph> glyphs, int budget) {
        String style = glyphs.get(0).style;
        int room = budget - style.length();
        if (room <= 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(budget);
        out.append(style);
        int limit = Math.min(glyphs.size(), room);
        for (int i = 0; i < limit; i++) {
            out.append(glyphs.get(i).ch);
        }
        return out.toString();
    }

    private static int boundary(String text, int cap) {
        int last = 0;
        int index = 0;
        while (index < text.length()) {
            int next = advance(text, index);
            if (next > cap) {
                return last;
            }
            last = next;
            index = next;
        }
        return last;
    }

    private static String activeStyle(String text, int cut) {
        String color = "";
        String formats = "";
        int index = 0;
        while (index < cut && index < text.length()) {
            if (text.charAt(index) != '§') {
                index++;
                continue;
            }
            int next = advance(text, index);
            if (next > cut) {
                break;
            }
            if (next - index == 14) {
                color = text.substring(index, next);
                formats = "";
            } else if (next - index == 2) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == 'r') {
                    color = "";
                    formats = "";
                } else if (isFormat(code)) {
                    formats = formats + text.substring(index, next);
                } else {
                    color = text.substring(index, next);
                    formats = "";
                }
            }
            index = next;
        }
        return color + formats;
    }

    private static List<Glyph> parse(String text) {
        List<Glyph> glyphs = new ArrayList<>();
        String color = "";
        String formats = "";
        for (int index = 0; index < text.length(); ) {
            if (text.charAt(index) == '§' && index + 1 < text.length()) {
                int next = advance(text, index);
                if (next - index == 14) {
                    color = text.substring(index, next);
                    formats = "";
                } else {
                    char code = Character.toLowerCase(text.charAt(index + 1));
                    if (code == 'r') {
                        color = "";
                        formats = "";
                    } else if (isFormat(code)) {
                        formats = formats + text.substring(index, next);
                    } else {
                        color = text.substring(index, next);
                        formats = "";
                    }
                }
                index = next;
                continue;
            }
            glyphs.add(new Glyph(color + formats, text.charAt(index)));
            index++;
        }
        return glyphs;
    }

    private static int advance(String text, int index) {
        if (index < text.length() && text.charAt(index) == '§' && index + 1 < text.length()) {
            char code = text.charAt(index + 1);
            if ((code == 'x' || code == 'X') && hexAt(text, index)) {
                return index + 14;
            }
            return index + 2;
        }
        return Math.min(text.length(), index + 1);
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

    private static boolean isFormat(char code) {
        return code == 'k' || code == 'l' || code == 'm' || code == 'n' || code == 'o';
    }

    private record Glyph(String style, char ch) {
    }
}
