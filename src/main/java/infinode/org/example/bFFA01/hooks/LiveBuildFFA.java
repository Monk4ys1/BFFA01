package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.api.BuildFFA;
import infinode.org.example.bFFA01.combat.DamageMath;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockPlayers;
import infinode.org.example.bFFA01.hooks.luck.Prefixes;
import infinode.org.example.bFFA01.hooks.papi.PluginStatSource;
import infinode.org.example.bFFA01.hooks.via.ViaPlayers;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * {@link BuildFFA} backed by the running plugin.
 */
public final class LiveBuildFFA implements BuildFFA {

    private final BFFA01 plugin;
    private final PluginStatSource stats;

    public LiveBuildFFA(BFFA01 plugin) {
        this.plugin = plugin;
        this.stats = new PluginStatSource(plugin);
    }

    @Override
    public String version() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String currentMap() {
        return stats.map();
    }

    @Override
    public Location currentSpawn() {
        try {
            return plugin.getMapManager() == null ? null : plugin.getMapManager().spawn();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public int kills(UUID player) {
        return stats.kills(player);
    }

    @Override
    public int deaths(UUID player) {
        return stats.deaths(player);
    }

    @Override
    public int coins(UUID player) {
        return stats.coins(player);
    }

    @Override
    public double killDeathRatio(UUID player) {
        int kills = kills(player);
        int deaths = deaths(player);
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    @Override
    public int streak(UUID player) {
        return stats.streak(player);
    }

    @Override
    public double damageDealt(UUID player) {
        if (player == null || plugin.getDataManager() == null) {
            return 0;
        }
        return DamageMath.hearts(plugin.getDataManager().damageDealt(player));
    }

    @Override
    public double damageTaken(UUID player) {
        if (player == null || plugin.getDataManager() == null) {
            return 0;
        }
        return DamageMath.hearts(plugin.getDataManager().damageTaken(player));
    }

    @Override
    public int assists(UUID player) {
        if (player == null || plugin.getDataManager() == null) {
            return 0;
        }
        return plugin.getDataManager().assists(player);
    }

    @Override
    public int leaderboardPosition(UUID player) {
        return stats.rank(player);
    }

    @Override
    public String kitName() {
        return stats.kit();
    }

    @Override
    public boolean bedrock(UUID player) {
        BedrockPlayers players = service(BedrockPlayers.class);
        return players != null && players.bedrock(player);
    }

    @Override
    public int protocolVersion(UUID player) {
        ViaPlayers via = service(ViaPlayers.class);
        return via == null ? -1 : via.protocol(player);
    }

    @Override
    public String clientVersion(UUID player) {
        ViaPlayers via = service(ViaPlayers.class);
        return via == null ? "" : via.client(player);
    }

    @Override
    public String prefix(Player player) {
        Prefixes prefixes = service(Prefixes.class);
        return prefixes == null || player == null ? "" : prefixes.prefix(player);
    }

    @Override
    public Set<String> activeIntegrations() {
        try {
            return plugin.integrations() == null ? Set.of() : plugin.integrations().activeIds();
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    private <T> T service(Class<T> type) {
        try {
            if (plugin.services() == null) {
                return null;
            }
            return plugin.services().get(type).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
