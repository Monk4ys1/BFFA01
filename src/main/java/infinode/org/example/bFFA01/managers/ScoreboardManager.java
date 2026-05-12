package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ScoreboardManager {
    private final BFFA01 plugin;
    private File file;
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
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("bffa", "dummy", ChatColor.translateAlternateColorCodes('&', config.getString("title", "&b&lBuildFFA")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = config.getStringList("lines");
        for (int i = 0; i < lines.size(); i++) {
            String teamName = "line" + i;
            Team team = board.registerNewTeam(teamName);
            String entry = ChatColor.values()[i % ChatColor.values().length].toString() + ChatColor.RESET;
            team.addEntry(entry);
            obj.getScore(entry).setScore(lines.size() - i);
        }

        player.setScoreboard(board);
        updateScoreboard(player);
    }

    public void updateScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();
        if (board == null) return;
        Objective obj = board.getObjective("bffa");
        if (obj == null) return;

        List<String> lines = config.getStringList("lines");
        
        int kills = plugin.getDataManager().getKills(player.getUniqueId());
        int deaths = plugin.getDataManager().getDeaths(player.getUniqueId());
        int streak = plugin.getKillstreakManager().getStreak(player);
        double kd = deaths == 0 ? kills : (double) kills / deaths;
        String currentMap = plugin.getMapManager().getCurrentMap() != null ? plugin.getMapManager().getCurrentMap() : "None";

        for (int i = 0; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            String processedLine = rawLine
                    .replace("%kills%", String.valueOf(kills))
                    .replace("%deaths%", String.valueOf(deaths))
                    .replace("%kd%", String.format("%.2f", kd))
                    .replace("%streak%", String.valueOf(streak))
                    .replace("%map%", currentMap);

            processedLine = ChatColor.translateAlternateColorCodes('&', processedLine);
            
            Team team = board.getTeam("line" + i);
            if (team != null) {
                team.setPrefix(processedLine);
            }
        }
    }

    private void startUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Update every 1 second
    }
}
