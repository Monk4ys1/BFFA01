package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.hooks.vault.CurrencyRouter;
import infinode.org.example.bFFA01.text.DisplayedText;
import infinode.org.example.bFFA01.ui.ClockText;
import infinode.org.example.bFFA01.ui.Presentation;
import infinode.org.example.bFFA01.ui.scoreboard.SidebarCache;
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
import java.util.ArrayList;
import java.util.HashMap;
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
    private final Map<UUID, Map<String, String>> inputs = new ConcurrentHashMap<>();
    private final Map<UUID, String> regions = new ConcurrentHashMap<>();

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
        inputs.clear();
    }

    public void forget(Player player) {
        if (player != null) {
            rendered.remove(player.getUniqueId());
            inputs.remove(player.getUniqueId());
            regions.remove(player.getUniqueId());
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
        inputs.remove(player.getUniqueId());
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
                forget(player);
                return;
            }
        } catch (Throwable ex) {
            return;
        }
        rememberRegion(player);
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
        Map<String, String> values = values(player);
        Map<String, String> priorValues = inputs.get(player.getUniqueId());
        if (previous != null && SidebarCache.same(priorValues, values)) {
            return;
        }
        if (priorValues == null || SidebarCache.lineAffected(title, priorValues, values)) {
            SidebarWriter.title(obj, DisplayedText.mini(plugin, player, title, values));
        }

        String[] next = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            if (previous != null && priorValues != null && !SidebarCache.lineAffected(rows.get(i), priorValues, values)) {
                next[i] = previous[i];
                continue;
            }
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
        inputs.put(player.getUniqueId(), values);
    }

    private void rememberRegion(Player player) {
        try {
            org.bukkit.Location location = player.getLocation();
            if (location.getWorld() == null) {
                return;
            }
            String key = location.getWorld().getUID() + ":" + (location.getBlockX() >> 9) + ":" + (location.getBlockZ() >> 9);
            regions.put(player.getUniqueId(), key);
        } catch (Throwable ignored) {
            // Location is only read on the player's own region thread.
        }
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
        java.util.Map<String, String> values = new java.util.HashMap<>();
        values.put("kills", Integer.toString(kills));
        values.put("deaths", Integer.toString(deaths));
        values.put("kd", String.format(java.util.Locale.US, "%.2f", kd));
        values.put("streak", Integer.toString(streak));
        values.put("coins", coins);
        values.put("players", Integer.toString(online));
        values.put("time", ClockText.mmss(seconds));
        values.put("map", currentMap);
        if (plugin.getDataManager() != null) {
            values.put("damage", infinode.org.example.bFFA01.combat.DamageMath.format(plugin.getDataManager().damageDealt(player.getUniqueId())));
            values.put("damage_taken", infinode.org.example.bFFA01.combat.DamageMath.format(plugin.getDataManager().damageTaken(player.getUniqueId())));
            values.put("assists", Integer.toString(plugin.getDataManager().assists(player.getUniqueId())));
        }
        return values;
    }

    private void startUpdateTask() {
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            return;
        }
        scheduler.runTimer(() -> {
            List<Player> online = new ArrayList<>();
            try {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player != null) {
                        online.add(player);
                    }
                }
            } catch (Throwable ex) {
                return;
            }
            if (!scheduler.folia()) {
                for (Player player : online) {
                    try {
                        updateScoreboard(player);
                    } catch (RuntimeException ex) {
                        plugin.getLogger().fine("Scoreboard update skipped: " + ex.getMessage());
                    }
                }
                return;
            }
            Map<String, List<Player>> groups = new HashMap<>();
            List<Player> solo = new ArrayList<>();
            for (Player player : online) {
                String key = regions.get(player.getUniqueId());
                if (key == null) {
                    solo.add(player);
                } else {
                    groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(player);
                }
            }
            for (Player player : solo) {
                scheduler.runOnEntity(player, () -> updateOne(player));
            }
            for (List<Player> group : groups.values()) {
                if (group.isEmpty()) {
                    continue;
                }
                Player anchor = group.get(0);
                scheduler.runOnEntity(anchor, () -> {
                    for (Player player : group) {
                        updateOne(player);
                    }
                });
            }
        }, 20L, 20L);
    }

    private void updateOne(Player player) {
        try {
            updateScoreboard(player);
        } catch (RuntimeException ex) {
            if (player != null) {
                regions.remove(player.getUniqueId());
            }
            plugin.getLogger().fine("Scoreboard update skipped: " + ex.getMessage());
        }
    }
}
