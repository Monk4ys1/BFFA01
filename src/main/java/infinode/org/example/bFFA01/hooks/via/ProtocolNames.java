package infinode.org.example.bFFA01.hooks.via;

import java.util.Map;

/**
 * Names for protocol numbers when ViaVersion's own table cannot be called.
 * Unknown numbers stay numeric.
 */
public final class ProtocolNames {

    private static final Map<Integer, String> NAMES = Map.ofEntries(
            Map.entry(763, "1.20-1.20.1"),
            Map.entry(764, "1.20.2"),
            Map.entry(765, "1.20.3-1.20.4"),
            Map.entry(766, "1.20.5-1.20.6"),
            Map.entry(767, "1.21-1.21.1"),
            Map.entry(768, "1.21.2-1.21.3"),
            Map.entry(769, "1.21.4"),
            Map.entry(770, "1.21.5"),
            Map.entry(771, "1.21.6"),
            Map.entry(772, "1.21.7-1.21.8")
    );

    private ProtocolNames() {
    }

    public static String friendly(int protocol) {
        return friendly(protocol, null);
    }

    public static String friendly(int protocol, String viaName) {
        if (viaName != null && !viaName.isBlank() && !"UNKNOWN".equalsIgnoreCase(viaName)) {
            return viaName;
        }
        if (protocol < 0) {
            return "";
        }
        return NAMES.getOrDefault(protocol, "protocol " + protocol);
    }
}
