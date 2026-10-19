package infinode.org.example.bFFA01.hooks.discord;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Discord webhooks only. Other hosts are rejected so a config typo cannot become a request
 * to an internal address.
 */
public final class WebhookAddress {

    private static final Pattern PATH = Pattern.compile("/api/webhooks/[0-9]{1,30}/[A-Za-z0-9._-]{1,120}/?");

    private static final Set<String> HOSTS = Set.of(
            "discord.com",
            "discordapp.com",
            "canary.discord.com",
            "ptb.discord.com"
    );

    private WebhookAddress() {
    }

    public static boolean allowed(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(raw.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || !HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
                return false;
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return false;
            }
            String path = uri.getRawPath();
            return path != null && PATH.matcher(path).matches();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
