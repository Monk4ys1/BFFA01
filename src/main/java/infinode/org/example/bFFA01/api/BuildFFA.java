package infinode.org.example.bFFA01.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Public API for other plugins. Obtain it from {@link BuildFFAProvider#get()}
 * or from Bukkit's services manager ({@code BuildFFA.class}).
 * Stats are the values BuildFFA has already stored. Listeners of the events in
 * {@code api.event} see the same numbers.
 */
public interface BuildFFA {

    String version();

    String currentMap();

    Location currentSpawn();

    int kills(UUID player);

    int deaths(UUID player);

    int coins(UUID player);

    double killDeathRatio(UUID player);

    int streak(UUID player);

    /**
     * Lifetime damage dealt, in health points. Overkill is not included.
     */
    double damageDealt(UUID player);

    /**
     * Lifetime damage taken, in health points.
     */
    double damageTaken(UUID player);

    int assists(UUID player);

    /**
     * 1-based kills leaderboard position. Tied players share a rank. {@code 0} when unknown.
     */
    int leaderboardPosition(UUID player);

    String kitName();

    boolean bedrock(UUID player);

    /**
     * Client protocol from ViaVersion, or {@code -1} when it is not known.
     */
    int protocolVersion(UUID player);

    String clientVersion(UUID player);

    /**
     * LuckPerms prefix when that plugin is active, otherwise the Vault chat prefix.
     */
    String prefix(Player player);

    Set<String> activeIntegrations();
}
