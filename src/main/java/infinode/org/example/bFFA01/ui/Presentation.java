package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.compat.Sounds;
import infinode.org.example.bFFA01.compat.TaskRunner;
import infinode.org.example.bFFA01.text.Messages;
import infinode.org.example.bFFA01.text.TextLines;
import infinode.org.example.bFFA01.ui.fx.NamedParticles;
import infinode.org.example.bFFA01.ui.menu.MenuCatalog;
import infinode.org.example.bFFA01.ui.menu.Menus;
import infinode.org.example.bFFA01.ui.prefs.PlayerPreferences;
import infinode.org.example.bFFA01.ui.scoreboard.SidebarFit;
import infinode.org.example.bFFA01.ui.theme.Theme;
import infinode.org.example.bFFA01.ui.vote.MapVotes;
import infinode.org.example.bFFA01.util.MapNames;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scoreboard chrome, menus, titles, and the first-join welcome.
 * Gameplay rules stay in the listeners; this class only changes how they look.
 */
public final class Presentation {

    private final BFFA01 plugin;
    private final Menus menus;
    private final MapVotes votes = new MapVotes();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tabTimes = new ConcurrentHashMap<>();
    private Theme theme = Theme.defaults();
    private MenuCatalog catalog = MenuCatalog.read("");
    private PresentationSettings settings = PresentationSettings.read("");
    private PlayerPreferences preferences;
    private TaskRunner.Cancellable task = TaskRunner.Cancellable.NONE;

    public Presentation(BFFA01 plugin) {
        this.plugin = plugin;
        this.menus = new Menus(this);
        this.preferences = new PlayerPreferences(new File(plugin.getDataFolder(), "preferences.yml"));
        reload();
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler != null) {
            task = scheduler.runTimer(this::tickBossbars, 20L, 20L);
        }
    }

    public BFFA01 plugin() {
        return plugin;
    }

    public Menus menus() {
        return menus;
    }

    public MenuCatalog catalog() {
        return catalog;
    }

    public Theme theme() {
        return theme;
    }

    public PresentationSettings settings() {
        return settings;
    }

    public PlayerPreferences preferences() {
        return preferences;
    }

    public MapVotes votes() {
        return votes;
    }

    public void reload() {
        save("theme.yml");
        save("menus.yml");
        save("presentation.yml");
        theme = Theme.load(read("theme.yml"));
        catalog = MenuCatalog.read(read("menus.yml"));
        settings = PresentationSettings.read(read("presentation.yml"));
        preferences.load();
        Messages messages = plugin.getMessages();
        if (messages != null) {
            messages.style(theme.colors(), theme.icons());
        }
        for (String issue : catalog.issues()) {
            plugin.getLogger().warning("Menu layout: " + issue);
        }
    }

    public void shutdown() {
        task.cancel();
        task = TaskRunner.Cancellable.NONE;
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    public void onJoin(Player player) {
        if (player == null) {
            return;
        }
        tab(player, true);
        welcome(player);
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        actionTimes.remove(id);
        tabTimes.remove(id);
        votes.remove(id);
        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void idleActionBar(Player player) {
        if (player == null || !settings.actionBar() || !preferences.actionbar(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = actionTimes.get(player.getUniqueId());
        if (previous != null && now - previous < 500L) {
            return;
        }
        actionTimes.put(player.getUniqueId(), now);
        String text = line(settings.actionBarKey(), placeholders(player));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
    }

    public void tab(Player player, boolean force) {
        if (player == null || !settings.tab()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force) {
            Long previous = tabTimes.get(player.getUniqueId());
            if (previous != null && now - previous < 1000L) {
                return;
            }
        }
        tabTimes.put(player.getUniqueId(), now);
        Map<String, String> values = placeholders(player);
        player.setPlayerListHeaderFooter(join(settings.tabHeader(), values), join(settings.tabFooter(), values));
    }

    public void refreshBossbar(Player player) {
        if (player == null) {
            return;
        }
        if (!settings.bossBar() || !preferences.bossbar(player.getUniqueId())) {
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) {
                bar.removeAll();
            }
            return;
        }
        updateBossbar(player);
    }

    public void onKill(Player killer, Player dead) {
        if (killer == null || dead == null) {
            return;
        }
        sound(killer, "kill");
        burst(dead.getLocation(), preferences.effect(killer.getUniqueId()));
        if (settings.titles() && !milestone(streakOf(killer) + 1)) {
            title(killer, "title-kill", "title-kill-sub", Map.of("player", dead.getName(), "killer", killer.getName()));
        }
    }

    public void onDeath(Player dead, String killer) {
        if (dead == null || !settings.titles()) {
            return;
        }
        sound(dead, "death");
        String name = killer == null || killer.isBlank() ? "" : killer;
        title(dead, "title-death", name.isEmpty() ? "" : "title-death-sub", Map.of("player", dead.getName(), "killer", name));
    }

    public void onStreak(Player player, int streak) {
        if (player == null || !milestone(streak)) {
            return;
        }
        sound(player, "streak");
        if (settings.titles()) {
            title(player, "title-streak", "title-streak-sub", Map.of("streak", Integer.toString(streak), "player", player.getName()));
        }
    }

    public void onMapSoon(Player player, int seconds) {
        if (player == null || (seconds != 5 && seconds != 3 && seconds != 2 && seconds != 1)) {
            return;
        }
        sound(player, "map");
        if (settings.titles()) {
            title(player, "title-map-soon", "title-map-soon-sub", Map.of("seconds", Integer.toString(seconds)));
        }
    }

    public void onMapArrived(Player player) {
        if (player == null) {
            return;
        }
        sound(player, "map");
        if (!settings.titles()) {
            return;
        }
        String map = currentMap();
        title(player, "title-map", "title-map-sub", Map.of("map", map));
    }

    public void sound(Player player, String cue) {
        if (player == null || cue == null) {
            return;
        }
        List<String> names = settings.sounds(cue);
        if (names.isEmpty()) {
            return;
        }
        Sounds.play(player, 0.7f, 1.1f, names.toArray(String[]::new));
    }

    public String line(String key, Map<String, String> values) {
        Messages messages = plugin.getMessages();
        if (messages == null) {
            return key == null ? "" : key;
        }
        return messages.render(key, values == null ? Map.of() : values);
    }

    public String prefixed(String key, Map<String, String> values) {
        return TextLines.prefixed(plugin, key, "", values == null ? Map.of() : values);
    }

    public Map<String, String> placeholders(Player player) {
        UUID id = player.getUniqueId();
        int kills = stat(id, true);
        int deaths = stat(id, false);
        int coins = plugin.getDataManager() == null ? 0 : plugin.getDataManager().getCoins(id);
        int streak = streakOf(player);
        double ratio = deaths == 0 ? kills : (double) kills / deaths;
        Map<String, String> values = new HashMap<>();
        values.put("player", safe(player.getName()));
        values.put("killer", safe(player.getName()));
        values.put("kills", Integer.toString(kills));
        values.put("deaths", Integer.toString(deaths));
        values.put("coins", Integer.toString(coins));
        values.put("streak", Integer.toString(streak));
        values.put("kd", String.format(Locale.US, "%.2f", ratio));
        values.put("map", currentMap());
        values.put("time", ClockText.mmss(secondsLeft()));
        values.put("seconds", Integer.toString(secondsLeft()));
        values.put("players", Integer.toString(Bukkit.getOnlinePlayers().size()));
        return values;
    }

    private void welcome(Player player) {
        if (!settings.firstJoin()) {
            return;
        }
        UUID id = player.getUniqueId();
        int kills = stat(id, true);
        int deaths = stat(id, false);
        int coins = plugin.getDataManager() == null ? 0 : plugin.getDataManager().getCoins(id);
        if (preferences.welcomed(id) || kills > 0 || deaths > 0 || coins > 0) {
            if (!preferences.welcomed(id)) {
                preferences.welcomed(id, true);
            }
            return;
        }
        preferences.welcomed(id, true);
        if (settings.firstJoinCoins() > 0 && plugin.getDataManager() != null) {
            plugin.getDataManager().addCoins(id, settings.firstJoinCoins());
        }
        sound(player, "join");
        if (settings.titles()) {
            title(player, "title-first-join", "title-first-join-sub", placeholders(player));
        }
        Map<String, String> values = placeholders(player);
        values.put("amount", Integer.toString(settings.firstJoinCoins()));
        for (String key : settings.firstJoinLines()) {
            player.sendMessage(prefixed(key, values));
        }
        if (settings.firstJoinCoins() > 0) {
            player.sendMessage(prefixed("first-join-coins", values));
        }
    }

    private void tickBossbars() {
        if (!settings.bossBar()) {
            for (BossBar bar : bars.values()) {
                bar.removeAll();
            }
            bars.clear();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!preferences.bossbar(player.getUniqueId())) {
                BossBar removed = bars.remove(player.getUniqueId());
                if (removed != null) {
                    removed.removeAll();
                }
                continue;
            }
            updateBossbar(player);
        }
    }

    private void updateBossbar(Player player) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_10));
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
        int seconds = secondsLeft();
        int interval = intervalSeconds();
        bar.setTitle(SidebarFit.compress(line(settings.bossBarKey(), placeholders(player)), 128));
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, seconds / (float) interval)));
        bar.setColor(seconds <= settings.lowSeconds() ? color(settings.bossLowColor(), BarColor.RED) : color(settings.bossColor(), BarColor.BLUE));
    }

    private void title(Player player, String titleKey, String subtitleKey, Map<String, String> values) {
        String title = titleKey == null || titleKey.isEmpty() ? "" : line(titleKey, values);
        String subtitle = subtitleKey == null || subtitleKey.isEmpty() ? "" : line(subtitleKey, values);
        player.sendTitle(title, subtitle, 5, 40, 12);
    }

    private void burst(Location location, String effect) {
        if (location == null || location.getWorld() == null || effect == null || "none".equals(effect)) {
            return;
        }
        List<String> names = settings.particles(effect);
        Location at = location.clone().add(0, 1, 0);
        Runnable spawn = () -> {
            Particle particle = NamedParticles.first(names);
            if (particle == null || at.getWorld() == null) {
                return;
            }
            try {
                at.getWorld().spawnParticle(particle, at, 28, 0.45, 0.65, 0.45, 0.01);
            } catch (RuntimeException ignored) {
                // A particle that needs extra data is skipped.
            }
        };
        ServerScheduler scheduler = plugin.scheduler();
        if (scheduler == null) {
            spawn.run();
        } else {
            scheduler.runAt(at, spawn, 0L);
        }
    }

    private String join(List<String> keys, Map<String, String> values) {
        StringBuilder out = new StringBuilder();
        for (String key : keys) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line(key, values));
        }
        return out.toString();
    }

    private int streakOf(Player player) {
        if (plugin.getKillstreakManager() == null || player == null) {
            return 0;
        }
        return plugin.getKillstreakManager().getStreak(player);
    }

    private boolean milestone(int streak) {
        return streak == 3 || (streak >= 5 && streak % 5 == 0);
    }

    private int stat(UUID id, boolean kills) {
        if (plugin.getDataManager() == null || id == null) {
            return 0;
        }
        return kills ? plugin.getDataManager().getKills(id) : plugin.getDataManager().getDeaths(id);
    }

    private int secondsLeft() {
        return plugin.getMapManager() == null ? 0 : plugin.getMapManager().secondsRemaining();
    }

    private int intervalSeconds() {
        try {
            return Math.max(1, plugin.getConfig().getInt("map-swap-interval", 600));
        } catch (Throwable ignored) {
            return 600;
        }
    }

    private String currentMap() {
        if (plugin.getMapManager() == null || plugin.getMapManager().getCurrentMap() == null) {
            return "None";
        }
        return MapNames.sanitizeLabel(plugin.getMapManager().getCurrentMap());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static BarColor color(String name, BarColor fallback) {
        try {
            return BarColor.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private void save(String resource) {
        if (!new File(plugin.getDataFolder(), resource).exists()) {
            plugin.saveResource(resource, false);
        }
    }

    private String read(String resource) {
        File file = new File(plugin.getDataFolder(), resource);
        if (file.isFile()) {
            try {
                return Files.readString(file.toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not read " + resource + ": " + ex.getMessage());
            }
        }
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                return "";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
