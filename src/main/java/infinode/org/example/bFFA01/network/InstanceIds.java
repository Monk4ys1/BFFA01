package infinode.org.example.bFFA01.network;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Identity for one process. A copied image or data folder must not pin two
 * replicas to the same id, so nothing is read from or written to server-id.txt.
 */
public final class InstanceIds {

    private static final AtomicReference<String> CURRENT = new AtomicReference<>("");
    private static final Set<String> GENERIC = Set.of(
            "local", "localhost", "localhostlocaldomain", "paper", "purpur", "spigot", "folia",
            "server", "minecraft", "bffa", "buildffa", "docker", "container", "host", "node"
    );

    private InstanceIds() {
    }

    public static String current() {
        String id = CURRENT.get();
        return id == null || id.isBlank() ? "local" : id;
    }

    public static void adopt(String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        String clipped = ServerIds.clip(id);
        if (!"local".equals(clipped)) {
            CURRENT.set(clipped);
        }
    }

    public static String propose() {
        return propose(System.getenv());
    }

    public static String propose(Map<String, String> env) {
        return propose(env, osHostname());
    }

    static String propose(Map<String, String> env, String osHost) {
        String explicit = value(env, "BFFA_SERVER_ID");
        if (distinct(explicit)) {
            return ServerIds.clip(explicit);
        }
        for (String key : new String[]{"POD_NAME", "HOSTNAME"}) {
            if (env != null && env.containsKey(key) && distinct(value(env, key))) {
                return ServerIds.clip(value(env, key));
            }
        }
        boolean named = env != null && (env.containsKey("POD_NAME") || env.containsKey("HOSTNAME"));
        if (!named && distinct(osHost)) {
            return ServerIds.clip(osHost);
        }
        return random();
    }

    private static String osHostname() {
        try {
            String host = java.net.InetAddress.getLocalHost().getHostName();
            return host == null ? "" : host;
        } catch (Exception ex) {
            return "";
        }
    }

    public static String random() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static boolean distinct(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String clipped = ServerIds.clip(raw);
        return !GENERIC.contains(clipped);
    }

    private static String value(Map<String, String> env, String key) {
        if (env == null) {
            return "";
        }
        String raw = env.get(key);
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
