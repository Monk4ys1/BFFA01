package infinode.org.example.bFFA01.hooks.papi;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import infinode.org.example.bFFA01.hooks.bedrock.BedrockPlayers;
import infinode.org.example.bFFA01.hooks.luck.Prefixes;
import infinode.org.example.bFFA01.hooks.vault.VaultBridge;
import infinode.org.example.bFFA01.hooks.via.ViaPlayers;
import infinode.org.example.bFFA01.storage.KillEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Live stats. Leaderboard ties break by name so the order does not flicker.
 */
public final class PluginStatSource implements StatSource {

    private final BFFA01 plugin;

    public PluginStatSource(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public int kills(UUID id) {
        if (id == null || plugin.getDataManager() == null) {
            return 0;
        }
        return plugin.getDataManager().getKills(id);
    }

    @Override
    public int deaths(UUID id) {
        if (id == null || plugin.getDataManager() == null) {
            return 0;
        }
        return plugin.getDataManager().getDeaths(id);
    }

    @Override
    public int coins(UUID id) {
        if (id == null || plugin.getDataManager() == null) {
            return 0;
        }
        return plugin.getDataManager().getCoins(id);
    }

    @Override
    public int streak(UUID id) {
        if (id == null || plugin.getKillstreakManager() == null) {
            return 0;
        }
        Player player = Bukkit.getPlayer(id);
        return player == null ? 0 : plugin.getKillstreakManager().getStreak(player);
    }

    @Override
    public int rank(UUID id) {
        if (id == null) {
            return 0;
        }
        List<KillEntry> board = board();
        int kills = kills(id);
        if (kills == 0 && board.stream().noneMatch(entry -> id.equals(entry.id()))) {
            return 0;
        }
        int ahead = 0;
        for (KillEntry entry : board) {
            if (entry.kills() > kills) {
                ahead++;
            }
        }
        return ahead + 1;
    }

    @Override
    public UUID find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (KillEntry entry : board()) {
            if (name.equalsIgnoreCase(entry.name())) {
                return entry.id();
            }
        }
        try {
            Player online = Bukkit.getPlayerExact(name);
            return online == null ? null : online.getUniqueId();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String map() {
        try {
            String map = plugin.getMapManager() == null ? null : plugin.getMapManager().getCurrentMap();
            return map == null ? "" : map;
        } catch (Throwable ignored) {
            return "";
        }
    }

    @Override
    public String kit() {
        return IntegrationSettings.kitName(IntegrationSettings.config(plugin));
    }

    @Override
    public String prefix(UUID id) {
        Player player = player(id);
        if (player == null) {
            return "";
        }
        Prefixes prefixes = service(Prefixes.class);
        return prefixes == null ? "" : prefixes.prefix(player);
    }

    @Override
    public boolean bedrock(UUID id) {
        BedrockPlayers players = service(BedrockPlayers.class);
        return players != null && players.bedrock(id);
    }

    @Override
    public int protocol(UUID id) {
        ViaPlayers via = service(ViaPlayers.class);
        return via == null ? -1 : via.protocol(id);
    }

    @Override
    public String client(UUID id) {
        ViaPlayers via = service(ViaPlayers.class);
        return via == null ? "" : via.client(id);
    }

    @Override
    public int online() {
        try {
            return Bukkit.getOnlinePlayers().size();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public String vault(UUID id) {
        VaultBridge bridge = service(VaultBridge.class);
        if (bridge == null || id == null || !bridge.available()) {
            return "";
        }
        return bridge.formatted(id);
    }

    @Override
    public String topName(int rank) {
        KillEntry entry = at(rank);
        return entry == null ? "" : entry.name();
    }

    @Override
    public int topKills(int rank) {
        KillEntry entry = at(rank);
        return entry == null ? 0 : entry.kills();
    }

    @Override
    public String topKd(int rank) {
        KillEntry entry = at(rank);
        if (entry == null) {
            return "";
        }
        int deaths = deaths(entry.id());
        double ratio = deaths == 0 ? entry.kills() : (double) entry.kills() / deaths;
        return String.format(Locale.US, "%.2f", ratio);
    }

    private KillEntry at(int rank) {
        List<KillEntry> board = board();
        if (rank < 1 || rank > board.size()) {
            return null;
        }
        return board.get(rank - 1);
    }

    private List<KillEntry> board() {
        if (plugin.getDataManager() == null) {
            return List.of();
        }
        List<KillEntry> copy = new ArrayList<>(plugin.getDataManager().topKills(Integer.MAX_VALUE));
        copy.sort(Comparator.comparingInt(KillEntry::kills).reversed()
                .thenComparing(entry -> entry.name() == null ? "" : entry.name(), String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private Player player(UUID id) {
        if (id == null) {
            return null;
        }
        try {
            return Bukkit.getPlayer(id);
        } catch (Throwable ignored) {
            return null;
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
