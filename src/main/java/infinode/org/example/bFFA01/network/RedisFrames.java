package infinode.org.example.bFFA01.network;

import java.nio.charset.StandardCharsets;

/**
 * RESP arrays for a tiny publish and subscribe client. No Redis library is shaded into the jar.
 */
public final class RedisFrames {

    private RedisFrames() {
    }

    public static String command(String... parts) {
        StringBuilder out = new StringBuilder();
        out.append('*').append(parts.length).append("\r\n");
        for (String part : parts) {
            String value = part == null ? "" : part;
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.append('$').append(bytes.length).append("\r\n").append(value).append("\r\n");
        }
        return out.toString();
    }

    public static String publish(String channel, String message) {
        return command("PUBLISH", channel, message);
    }

    public static String subscribe(String channel) {
        return command("SUBSCRIBE", channel);
    }

    public static String auth(String password) {
        return command("AUTH", password);
    }
}
