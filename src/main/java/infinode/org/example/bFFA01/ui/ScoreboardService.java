package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.config.YamlFile;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar scoreboard, tab list and the red/green name colouring.
 *
 * <p>Lines are rendered through team prefixes on invisible entries, so the
 * sidebar updates without the flicker a full re-register causes. 4.x used
 * {@code ChatColor.values()[i]} for those entries, which produces duplicates
 * past 22 lines and silently drops them; 5.x builds a unique two-code entry per
 * line and splits long lines across prefix and suffix.
 */
public final class ScoreboardService {

    private static final String OBJECTIVE_NAME = "bffa";
    private static final String COMBAT_TEAM = "bffa_combat";
    private static final String SAFE_TEAM = "bffa_safe";
    private static final String LINE_TEAM_PREFIX = "bffa_line";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    /** Vanilla only renders 15 sidebar lines. */
    private static final int MAX_LINES = 15;

    private final BFFA01 plugin;
    private final YamlFile storage;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private String title = "&b&lBuildFFA";
    private List<String> lines = List.of();
    private BukkitTask task;

    public ScoreboardService(BFFA01 plugin) {
        this.plugin = plugin;
        this.storage = new YamlFile(plugin, "scoreboard.yml", true);
        reload();
    }

    public void reload() {
        storage.reload();
        title = storage.config().getString("title", "&b&lBuildFFA");
        List<String> configured = storage.config().getStringList("lines");
        if (configured.size() > MAX_LINES) {
            plugin.getLogger().warning("scoreboard.yml has " + configured.size()
                    + " lines, only the first " + MAX_LINES + " are shown.");
            configured = configured.subList(0, MAX_LINES);
        }
        lines = configured;
    }

    public void start() {
        stop();
        if (!plugin.settings().scoreboardEnabled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                update(player);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Builds a player's board. Called on join and after a reload. */
    public void setup(Player player) {
        if (!plugin.settings().scoreboardEnabled()) {
            return;
        }
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Text.colorize(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (int index = 0; index < lines.size(); index++) {
            String entry = entryFor(index);
            Team team = board.registerNewTeam(LINE_TEAM_PREFIX + index);
            team.addEntry(entry);
            objective.getScore(entry).setScore(lines.size() - index);
        }

        registerNameTeam(board, COMBAT_TEAM, ChatColor.RED);
        registerNameTeam(board, SAFE_TEAM, ChatColor.GREEN);

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);

        // Make every player visible in the right colour on this new board, and
        // this player visible on everyone else's.
        for (Player other : Bukkit.getOnlinePlayers()) {
            applyColour(board, other.getName(), plugin.combatManager().isTagged(other));
        }
        updateCombatColour(player, plugin.combatManager().isTagged(player));

        update(player);
        updateTabList(player);
    }

    private void registerNameTeam(Scoreboard board, String name, ChatColor colour) {
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        try {
            team.setColor(colour);
        } catch (Throwable ignored) {
            team.setPrefix(colour.toString());
        }
    }

    /** Drops a player's board and removes them from everyone else's teams. */
    public void remove(Player player) {
        boards.remove(player.getUniqueId());
        String name = player.getName();
        for (Scoreboard board : boards.values()) {
            Team combat = board.getTeam(COMBAT_TEAM);
            if (combat != null) {
                combat.removeEntry(name);
            }
            Team safe = board.getTeam(SAFE_TEAM);
            if (safe != null) {
                safe.removeEntry(name);
            }
        }
    }

    /** Switches a player's name colour on every board. */
    public void updateCombatColour(Player player, boolean inCombat) {
        String name = player.getName();
        for (Scoreboard board : boards.values()) {
            applyColour(board, name, inCombat);
        }
        if (plugin.settings().colourNames()) {
            player.setPlayerListName((inCombat ? ChatColor.RED : ChatColor.GREEN) + name);
        }
    }

    private void applyColour(Scoreboard board, String name, boolean inCombat) {
        // addEntry moves the entry off the other team automatically.
        Team target = board.getTeam(inCombat ? COMBAT_TEAM : SAFE_TEAM);
        if (target != null && !target.hasEntry(name)) {
            target.addEntry(name);
        }
    }

    /** Re-renders the sidebar of one player. */
    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        PlayerData data = plugin.dataManager().get(player);
        // rankOf scans every profile, so resolve it once per update.
        int rank = plugin.dataManager().rankOf(player.getUniqueId());
        int streak = plugin.killstreakManager().streak(player);
        for (int index = 0; index < lines.size(); index++) {
            Team team = board.getTeam(LINE_TEAM_PREFIX + index);
            if (team == null) {
                continue;
            }
            String[] parts = Text.splitForTeam(Text.colorize(fill(lines.get(index), player, data, rank, streak)));
            if (!parts[0].equals(team.getPrefix())) {
                team.setPrefix(parts[0]);
            }
            if (!parts[1].equals(team.getSuffix())) {
                team.setSuffix(parts[1]);
            }
        }
    }

    /** Replaces every scoreboard placeholder. */
    private String fill(String line, Player player, PlayerData data, int rank, int streak) {
        return line
                .replace("%player%", player.getName())
                .replace("%kills%", String.valueOf(data.kills()))
                .replace("%deaths%", String.valueOf(data.deaths()))
                .replace("%kd%", String.format("%.2f", data.kd()))
                .replace("%coins%", String.valueOf(data.coins()))
                .replace("%streak%", String.valueOf(streak))
                .replace("%best_streak%", String.valueOf(data.bestStreak()))
                .replace("%rank%", rank > 0 ? "#" + rank : "-")
                .replace("%map%", plugin.mapManager().currentName())
                .replace("%time%", Text.formatTime(plugin.mapManager().secondsUntilSwap()))
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    /** Applies the configured tab list header and footer. */
    public void updateTabList(Player player) {
        if (!plugin.settings().tablistEnabled()) {
            return;
        }
        try {
            player.setPlayerListHeaderFooter(
                    Text.colorize(String.join("\n", plugin.settings().tablistHeader())),
                    Text.colorize(String.join("\n", plugin.settings().tablistFooter())));
        } catch (Throwable ignored) {
            // Cosmetic only.
        }
    }

    /** Rebuilds every online player's board, used by {@code /bffa reload}. */
    public void refreshAll() {
        boards.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            setup(player);
        }
    }

    /**
     * Unique invisible sidebar entry for a line index. Two colour codes give
     * 256 distinct entries, far more than the 15 lines a sidebar can show.
     */
    private static String entryFor(int index) {
        return String.valueOf(ChatColor.COLOR_CHAR) + HEX[(index / 16) & 0xF]
                + ChatColor.COLOR_CHAR + HEX[index & 0xF]
                + ChatColor.COLOR_CHAR + 'r';
    }
}
