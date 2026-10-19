package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.PlaceholderResolver;
import infinode.org.example.bFFA01.text.Messages;
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

public class ScoreboardManager {
    private final BFFA01 plugin;
    private final File file;
    private FileConfiguration config;

    public ScoreboardManager(BFFA01 plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!this.file.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        startUpdateTask();
    }

    public void reloadConfig() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public void setScoreboard(Player player) {
        Presentation presentation = plugin.presentation();
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
        List<String> lines = config.getStringList("lines");
        Objective obj = board.registerNewObjective("bffa", "dummy", "BuildFFA");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        SidebarWriter.title(obj, themed(config.getString("title", "<bold>BuildFFA")));

        for (int i = 0; i < lines.size(); i++) {
            Team team = board.registerNewTeam("line" + i);
            String entry = SidebarFit.entry(i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(lines.size() - i);
        }

        player.setScoreboard(board);
        updateScoreboard(player);
    }

    public void updateScoreboard(Player player) {
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
        List<String> lines = config.getStringList("lines");
        SidebarWriter.title(obj, themed(config.getString("title", "<bold>BuildFFA")));

        int kills = plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager().getDeaths(player.getUniqueId());
        int coins = plugin.getDataManager().getCoins(player.getUniqueId());
        int streak = plugin.getKillstreakManager().getStreak(player);
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String currentMap = plugin.getMapManager().getCurrentMap() != null
                ? MapNames.sanitizeLabel(plugin.getMapManager().getCurrentMap())
                : "None";
        int seconds = plugin.getMapManager() == null ? 0 : plugin.getMapManager().secondsRemaining();

        PlaceholderResolver resolver = resolver();
        for (int i = 0; i < lines.size(); i++) {
            String processedLine = lines.get(i)
                    .replace("%kills%", String.valueOf(kills))
                    .replace("%deaths%", String.valueOf(deaths))
                    .replace("%kd%", String.format("%.2f", kd))
                    .replace("%streak%", String.valueOf(streak))
                    .replace("%coins%", String.valueOf(coins))
                    .replace("%players%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                    .replace("%time%", ClockText.mmss(seconds))
                    .replace("%map%", currentMap);
            processedLine = resolver.apply(player, processedLine);
            processedLine = themed(processedLine);

            String entry = SidebarFit.entry(i);
            Team team = board.getTeam("line" + i);
            if (team == null) {
                team = board.registerNewTeam("line" + i);
                team.addEntry(entry);
                obj.getScore(entry).setScore(lines.size() - i);
            }
            Score score = obj.getScore(entry);
            SidebarWriter.line(team, score, processedLine, i);
        }
    }

    private String themed(String line) {
        Messages messages = plugin.getMessages();
        if (messages == null || line == null) {
            return line == null ? "" : line;
        }
        return messages.theme(line);
    }

    private PlaceholderResolver resolver() {
        try {
            if (plugin.services() == null) {
                return PlaceholderResolver.identity();
            }
            return plugin.services().get(PlaceholderResolver.class).orElseGet(PlaceholderResolver::identity);
        } catch (Throwable ignored) {
            return PlaceholderResolver.identity();
        }
    }

    private void startUpdateTask() {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        scheduler.runTimer(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scheduler.runOnEntity(player, () -> {
                    try {
                        updateScoreboard(player);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().fine("Scoreboard update skipped for " + player.getName() + ": " + ex.getMessage());
                    }
                });
            }
        }, 20L, 20L);
    }
}
