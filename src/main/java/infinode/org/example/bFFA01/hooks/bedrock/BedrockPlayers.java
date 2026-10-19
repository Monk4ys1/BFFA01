package infinode.org.example.bFFA01.hooks.bedrock;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * Bedrock detection from Floodgate and, if that is absent, Geyser.
 */
public final class BedrockPlayers {

    private volatile Predicate<UUID> probe = id -> false;

    public void use(Predicate<UUID> next) {
        Predicate<UUID> current = probe;
        Predicate<UUID> extra = next == null ? id -> false : next;
        probe = id -> test(current, id) || test(extra, id);
    }

    public void clear() {
        probe = id -> false;
    }

    public boolean bedrock(UUID id) {
        return id != null && test(probe, id);
    }

    private static boolean test(Predicate<UUID> probe, UUID id) {
        try {
            return probe.test(id);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
