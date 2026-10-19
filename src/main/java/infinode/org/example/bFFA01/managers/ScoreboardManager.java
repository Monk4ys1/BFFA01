package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.text.DisplayedText;
import infinode.org.example.bFFA01.ui.ClockText;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.ui.scoreboard.SidebarFit;
import infinode.org.example.bFFA01.ui.scoreboard.SidebarWriter;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {
    private final BFFA01 plugin;
    private final File file;
    private volatile List<String> lines = List.of();
    private volatile String title = "<bold>BuildFFA";
    private final Map<UUID, String[]> rendered = new ConcurrentHashMap<>();

    public ScoreboardManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!this.file.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }
        readFile();
        startUpdateTask();
    }

    public void reloadConfig() {
        readFile();
        rendered.clear();
    }

    public void forget(Player player) {
        if (player != null) {
            rendered.remove(player.getUniqueId());
        }
    }

    private void readFile() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.lines = List.copyOf(config.getStringList("lines"));
        String configured = config.getString("title", "<bold>BuildFFA");
        this.title = configured == null ? "<bold>BuildFFA" : configured;
    }

    public void setScoreboard(Player player) {
        if (player == null) {
            return;
        }
        Presentation presentation = plugin.presentation();
        rendered.remove(player.getUniqueId());
        if (presentation != null && !presentation.preferences().scoreboard(player.getUniqueId())) {
            if (Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        List<String> rows = lines;
        Objective obj = board.registerNewObjective("bffa", "dummy", "BuildFFA");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        SidebarWriter.title(obj, DisplayedText.mini(plugin, player, title, java.util.Map.of()));

        for (int i = 0; i < rows.size(); i++) {
            Team team = board.registerNewTeam("line" + i);
            String entry = SidebarFit.entry(i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(rows.size() - i);
        }

        player.setScoreboard(board);
        updateScoreboard(player);
    }

    public void updateScoreboard(Player player) {
        if (player == null) {
            return;
        }
        try {
            if (!player.isOnline()) {
                rendered.remove(player.getUniqueId());
                return;
            }
        } catch (Throwable ex) {
            return;
        }
        Scoreboard board = player.getScoreboard();
        if (board == null) {
            return;
        }
        Objective obj = board.getObjective("bffa");
        if (obj == null) {
            return;
        }

        Presentation presentation = plugin.presentation();
        if (presentation != null && !presentation.preferences().scoreboard(player.getUniqueId())) {
            return;
        }
        List<String> rows = lines;
        String[] previous = rendered.get(player.getUniqueId());
        if (previous != null && previous.length != rows.size()) {
            setScoreboard(player);
            return;
        }
        java.util.Map<String, String> values = values(player);
        SidebarWriter.title(obj, DisplayedText.mini(plugin, player, title, values));

        String[] next = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String processedLine = DisplayedText.mini(plugin, player, rows.get(i), values);
            next[i] = processedLine;
            if (previous != null && processedLine.equals(previous[i])) {
                continue;
            }
            String entry = SidebarFit.entry(i);
            Team team = board.getTeam("line" + i);
            if (team == null) {
                team = board.registerNewTeam("line" + i);
                team.addEntry(entry);
                obj.getScore(entry).setScore(rows.size() - i);
            }
            Score score = obj.getScore(entry);
            SidebarWriter.line(team, score, processedLine, i);
        }
        rendered.put(player.getUniqueId(), next);
    }

    private java.util.Map<String, String> values(Player player) {
        int kills = plugin.getDataManager() == null ? 0 : plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager() == null ? 0 : plugin.getDataManager().getDeaths(player.getUniqueId());
        int streak = plugin.getKillstreakManager() == null ? 0 : plugin.getKillstreakManager().getStreak(player);
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String currentMap = plugin.getMapManager() == null || plugin.getMapManager().getCurrentMap() == null
                ? "None"
                : MapNames.sanitizeLabel(plugin.getMapManager().getCurrentMap());
        int seconds = plugin.getMapManager() == null ? 0 : plugin.getMapManager().secondsRemaining();
        String coins;
        try {
            coins = CurrencyRouter.get(plugin).balance(player);
        } catch (Throwable ex) {
            coins = Integer.toString(plugin.getDataManager() == null ? 0 : plugin.getDataManager().getCoins(player.getUniqueId()));
        }
        int online = 0;
        try {
            online = Bukkit.getOnlinePlayers().size();
        } catch (Throwable ignored) {
            online = 0;
        }
        return java.util.Map.of(
                "kills", Integer.toString(kills),
                "deaths", Integer.toString(deaths),
                "kd", String.format(java.util.Locale.US, "%.2f", kd),
                "streak", Integer.toString(streak),
                "coins", coins,
                "players", Integer.toString(online),
                "time", ClockText.mmss(seconds),
                "map", currentMap
        );
    }

    private void startUpdateTask() {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        scheduler.runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null) {
                    continue;
                }
                scheduler.runOnEntity(player, () -> {
                    try {
                        updateScoreboard(player);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().fine("Scoreboard update skipped: " + ex.getMessage());
                    }
                });
            }
        }, 20L, 20L);
    }
}
