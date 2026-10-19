package infinode.org.example.bFFA01.hooks.json;

import java.util.Locale;

/**
 * Minimal JSON writer for webhook and metrics bodies. No third-party parser.
 */
public final class Json {

    private final StringBuilder out = new StringBuilder();
    private boolean first = true;
    private boolean closed;

    private Json(char open) {
        out.append(open);
    }

    public static Json object() {
        return new Json('{');
    }

    public static Json array() {
        return new Json('[');
    }

    public Json add(String key, String value) {
        key(key);
        out.append(quote(value));
        return this;
    }

    public Json add(String key, int value) {
        key(key);
        out.append(value);
        return this;
    }

    public Json add(String key, boolean value) {
        key(key);
        out.append(value);
        return this;
    }

    public Json addRaw(String key, String json) {
        key(key);
        out.append(json == null ? "null" : json);
        return this;
    }

    public Json item(String value) {
        comma();
        out.append(quote(value));
        return this;
    }

    public Json itemRaw(String json) {
        comma();
        out.append(json == null ? "null" : json);
        return this;
    }

    public String build() {
        if (!closed) {
            out.append(out.charAt(0) == '{' ? '}' : ']');
            closed = true;
        }
        return out.toString();
    }

    public static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private void key(String key) {
        comma();
        out.append(quote(key)).append(':');
    }

    private void comma() {
        if (!first) {
            out.append(',');
        }
        first = false;
    }
}
