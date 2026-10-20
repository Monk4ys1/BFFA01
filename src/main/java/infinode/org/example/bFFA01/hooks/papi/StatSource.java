package infinode.org.example.bFFA01.hooks.papi;

import java.util.UUID;

/**
 * Read-only stats used by placeholders. Implementations may touch the server.
 * {@link BffaTokens} itself does not.
 */
public interface StatSource {

    int kills(UUID id);

    int deaths(UUID id);

    int coins(UUID id);

    int streak(UUID id);

    int rank(UUID id);

    UUID find(String name);

    String map();

    String kit();

    String prefix(UUID id);

    boolean bedrock(UUID id);

    int protocol(UUID id);

    String client(UUID id);

    int online();

    String vault(UUID id);

    String topName(int rank);

    int topKills(int rank);

    String topKd(int rank);

    default String damage(UUID id) {
        return "0";
    }

    default String damageTaken(UUID id) {
        return "0";
    }

    default String lifeDamage(UUID id) {
        return "0";
    }

    default String lifeTaken(UUID id) {
        return "0";
    }

    default int assists(UUID id) {
        return 0;
    }

    default String topDamage(int rank) {
        return "";
    }

    default String topDamageName(int rank) {
        return "";
    }
}
