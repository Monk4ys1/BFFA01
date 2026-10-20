package infinode.org.example.bFFA01.network;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * HMAC-SHA256 on cross-instance frames. The tag is the first 8 bytes, hex encoded.
 * A blank secret rejects every frame. Unsigned Redis and plugin-channel messages
 * are both dropped.
 */
public final class NetworkSeal {

    public static final long MAX_SKEW_MILLIS = 30_000L;
    public static final int MAX_FRAME = 240;

    private NetworkSeal() {
    }

    public record Frame(String id, String kind, String body, long time) {
    }

    public static String encode(String secret, String id, String kind, String body, long time) {
        String unsigned = "1|" + id + "|" + kind + "|" + body + "|" + time;
        if (secret == null || secret.isEmpty()) {
            return unsigned;
        }
        return unsigned + "|" + tag(secret, unsigned);
    }

    /**
     * @param pluginChannel true when the bytes arrived on {@code bffa:net}
     */
    public static Frame decode(String secret, String text, long now, boolean pluginChannel) {
        if (text == null || text.length() > MAX_FRAME || !text.startsWith("1|")) {
            return null;
        }
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        int cut = text.lastIndexOf('|');
        if (cut < 8 || cut >= text.length() - 1) {
            return null;
        }
        String presented = text.substring(cut + 1);
        String unsigned = text.substring(0, cut);
        if (!same(tag(secret, unsigned), presented)) {
            return null;
        }
        int timeCut = unsigned.lastIndexOf('|');
        if (timeCut <= 0) {
            return null;
        }
        long time;
        try {
            time = Long.parseLong(unsigned.substring(timeCut + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (time < 0L || Math.abs(now - time) > MAX_SKEW_MILLIS) {
            return null;
        }
        String[] parts = unsigned.substring(0, timeCut).split("\\|", 4);
        if (parts.length < 4 || !"1".equals(parts[0])) {
            return null;
        }
        if (!parts[1].matches("[A-Za-z0-9._-]{1,32}")) {
            return null;
        }
        if (!"board".equals(parts[2]) && !"streak".equals(parts[2])) {
            return null;
        }
        if ("streak".equals(parts[2]) && !streakBody(parts[3])) {
            return null;
        }
        return new Frame(parts[1], parts[2], parts[3], time);
    }

    private static boolean streakBody(String body) {
        int split = body.lastIndexOf('|');
        if (split <= 0 || split >= body.length() - 1) {
            return false;
        }
        try {
            int streak = Integer.parseInt(body.substring(split + 1));
            return streak >= 1 && streak <= 10_000;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String tag(String secret, String unsigned) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] full = mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", full[i]));
            }
            return hex.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean same(String expected, String presented) {
        if (expected.isEmpty() || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
