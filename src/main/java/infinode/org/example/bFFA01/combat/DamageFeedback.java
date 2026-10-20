package infinode.org.example.bFFA01.combat;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.api.DamageCredit;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.text.DisplayedText;
import infinode.org.example.bFFA01.text.Messages;
import infinode.org.example.bFFA01.text.TextLines;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Floating hit numbers, the combat action bar, and the kill or death summary.
 * Indicators live a few ticks, merge rapid hits, and stay capped per victim.
 */
public final class DamageFeedback {

    static final String TAG = "bffa_damage";
    private static final int CAP = 4;

    private final BFFA01 plugin;
    private final Map<UUID, List<Live>> indicators = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionUntil = new ConcurrentHashMap<>();
    private volatile DamageSettings settings = DamageSettings.load(null, null);

    public DamageFeedback(BFFA01 plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration presentation = new YamlConfiguration();
        File file = new File(plugin.getDataFolder(), "presentation.yml");
        try {
            if (file.isFile()) {
                presentation.loadFromString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            presentation = new YamlConfiguration();
        }
        org.bukkit.configuration.file.FileConfiguration config = null;
        try {
            config = plugin.getConfig();
        } catch (Throwable ignored) {
            config = null;
        }
        settings = DamageSettings.load(config, presentation);
    }

    public DamageSettings settings() {
        return settings;
    }

    public boolean holdsActionBar(UUID id, long now) {
        Long until = id == null ? null : actionUntil.get(id);
        return until != null && now < until;
    }

    public void showHit(Player attacker, Player victim, Location at, long hundredths, double remaining, long combo,
                        boolean crit, boolean sweep) {
        DamageSettings current = settings;
        if (!current.enabled() || hundredths <= 0 || victim == null) {
            return;
        }
        boolean attackerSees = sees(attacker);
        boolean victimSees = sees(victim);
        if (!attackerSees && !victimSees) {
            return;
        }
        Map<String, String> values = values(hundredths, remaining, combo, "");
        if (current.indicators() && at != null && (attackerSees || victimSees)) {
            String key = crit ? "damage-indicator-crit" : sweep ? "damage-indicator-sweep" : "damage-indicator";
            spawn(victim, at, key, values, current);
        }
        if (current.actionBar() && attackerSees && attacker != null) {
            String key = crit ? "damage-actionbar-crit" : sweep ? "damage-actionbar-sweep" : "damage-actionbar";
            tell(attacker, key, values);
        }
        if (current.actionBar() && victimSees) {
            tell(victim, "damage-actionbar-taken", values);
        }
    }

    public void summarizeKill(Player killer, String victimName, double damage, List<DamageCredit> assists) {
        DamageSettings current = settings;
        if (!current.enabled() || !current.summary() || !sees(killer)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(TextLines.render(plugin, "damage-summary-kill", "&aYou dealt &f%damage% &ato &f%player%&a.",
                Map.of("damage", DamageMath.format(Math.round(damage * 100.0)), "player", victimName == null ? "" : victimName)));
        appendRows(lines, assists);
        deliver(killer, lines);
    }

    public void summarizeDeath(Player victim, List<DamageCredit> breakdown) {
        DamageSettings current = settings;
        if (!current.enabled() || !current.summary() || !sees(victim) || breakdown == null || breakdown.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(TextLines.render(plugin, "damage-summary-death", "&cDamage taken:", Map.of()));
        appendRows(lines, breakdown);
        deliver(victim, lines);
    }

    public void clear() {
        for (List<Live> lives : indicators.values()) {
            for (Live live : List.copyOf(lives)) {
                remove(live.entity);
            }
        }
        indicators.clear();
        actionUntil.clear();
    }

    private void appendRows(List<String> lines, List<DamageCredit> credits) {
        if (credits == null) {
            return;
        }
        int shown = 0;
        for (DamageCredit credit : credits) {
            if (shown == 5) {
                break;
            }
            shown++;
            lines.add(TextLines.render(plugin, "damage-summary-row", "&7%player% &f%damage% &8%weapon%",
                    Map.of(
                            "player", credit.name(),
                            "damage", DamageMath.format(Math.round(credit.damage() * 100.0)),
                            "weapon", credit.weapon()
                    )));
        }
    }

    private void deliver(Player player, List<String> lines) {
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> {
            if (!online(player)) {
                return;
            }
            for (String line : lines) {
                player.sendMessage(line);
            }
        };
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.runOnEntity(player, task);
        }
    }

    private void tell(Player player, String key, Map<String, String> values) {
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> {
            if (!online(player) || !sees(player)) {
                return;
            }
            actionUntil.put(player.getUniqueId(), System.currentTimeMillis() + 2_000L);
            String text = TextLines.render(plugin, key, "&e%amount%", values);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text));
        };
        if (scheduler == null) {
            task.run();
        } else {
            scheduler.runOnEntity(player, task);
        }
    }

    private void spawn(Player victim, Location at, String key, Map<String, String> values, DamageSettings current) {
        List<Live> lives = indicators.computeIfAbsent(victim.getUniqueId(), ignored -> new ArrayList<>());
        long now = System.currentTimeMillis();
        synchronized (lives) {
            lives.removeIf(live -> !live.entity.isValid());
            Live recent = lives.isEmpty() ? null : lives.get(lives.size() - 1);
            if (recent != null && now - recent.shownAt <= current.mergeMillis()) {
                recent.amount += hundredths(values);
                recent.generation++;
                recent.shownAt = now;
                Map<String, String> merged = values(recent.amount, remaining(values), combo(values), "");
                applyText(recent.entity, key, merged);
                scheduleRemoval(victim, recent, current.lifetimeTicks());
                return;
            }
            while (lives.size() >= CAP) {
                Live oldest = lives.remove(0);
                oldest.generation++;
                remove(oldest.entity);
            }
            Location spot = at.clone();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            spot.add(random.nextDouble(-0.25, 0.25), 0.35 + random.nextDouble(0.35), random.nextDouble(-0.25, 0.25));
            Entity entity = spawnEntity(spot, key, values);
            if (entity == null) {
                return;
            }
            Live live = new Live(entity, hundredths(values), now);
            lives.add(live);
            scheduleRemoval(victim, live, current.lifetimeTicks());
        }
    }

    private Entity spawnEntity(Location at, String key, Map<String, String> values) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        Entity display = textDisplay(world, at, key, values);
        if (display != null) {
            return display;
        }
        return armorStand(world, at, key, values);
    }

    private Entity textDisplay(World world, Location at, String key, Map<String, String> values) {
        try {
            Class<?> type = Class.forName("org.bukkit.entity.TextDisplay");
            if (!Entity.class.isAssignableFrom(type)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Entity> kind = (Class<? extends Entity>) type;
            Entity entity = world.spawn(at, kind);
            tag(entity);
            try {
                Class<?> billboard = Class.forName("org.bukkit.entity.Display$Billboard");
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object center = Enum.valueOf((Class) billboard, "CENTER");
                entity.getClass().getMethod("setBillboard", billboard).invoke(entity, center);
            } catch (Throwable ignored) {
                // Billboard is optional. The text still faces the default direction.
            }
            try {
                entity.getClass().getMethod("setSeeThrough", boolean.class).invoke(entity, true);
            } catch (Throwable ignored) {
                // Older display entities skip this.
            }
            try {
                entity.getClass().getMethod("setViewRange", float.class).invoke(entity, 12.0f);
            } catch (Throwable ignored) {
                // Default view range is fine when the method is missing.
            }
            if (!applyText(entity, key, values)) {
                entity.remove();
                return null;
            }
            return entity;
        } catch (Throwable ex) {
            return null;
        }
    }

    private Entity armorStand(World world, Location at, String key, Map<String, String> values) {
        try {
            ArmorStand stand = world.spawn(at, ArmorStand.class);
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setCustomNameVisible(true);
            tag(stand);
            stand.setCustomName(legacy(key, values));
            return stand;
        } catch (Throwable ex) {
            return null;
        }
    }

    private boolean applyText(Entity entity, String key, Map<String, String> values) {
        if (entity instanceof ArmorStand stand) {
            stand.setCustomName(legacy(key, values));
            return true;
        }
        Object component = ServerText.component(mini(key, values));
        if (component == null) {
            return false;
        }
        for (Method method : entity.getClass().getMethods()) {
            if ("text".equals(method.getName()) && method.getParameterCount() == 1) {
                try {
                    method.invoke(entity, component);
                    return true;
                } catch (Throwable ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private void scheduleRemoval(Player victim, Live live, int ticks) {
        int generation = live.generation;
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> {
            if (live.generation != generation) {
                return;
            }
            remove(live.entity);
            List<Live> lives = indicators.get(victim.getUniqueId());
            if (lives != null) {
                synchronized (lives) {
                    lives.remove(live);
                }
            }
        };
        if (scheduler == null) {
            return;
        }
        scheduler.runOnEntityLater(live.entity, task, ticks);
    }

    private void remove(Entity entity) {
        if (entity == null) {
            return;
        }
        ServerScheduler scheduler = plugin.scheduler();
        Runnable task = () -> {
            try {
                if (entity.isValid()) {
                    entity.remove();
                }
            } catch (Throwable ignored) {
                // The region is already gone.
            }
        };
        if (scheduler == null || !scheduler.folia()) {
            task.run();
        } else {
            scheduler.runOnEntity(entity, task);
        }
    }

    private void tag(Entity entity) {
        entity.addScoreboardTag(TAG);
        entity.setPersistent(false);
        try {
            entity.setInvulnerable(true);
        } catch (Throwable ignored) {
            // Displays on some builds reject this.
        }
    }

    private boolean sees(Player player) {
        if (player == null) {
            return false;
        }
        try {
            if (plugin.presentation() == null) {
                return true;
            }
            return plugin.presentation().preferences().damage(player.getUniqueId());
        } catch (Throwable ignored) {
            return true;
        }
    }

    private String mini(String key, Map<String, String> values) {
        Messages messages = null;
        try {
            messages = plugin.getMessages();
        } catch (Throwable ignored) {
            messages = null;
        }
        String template = messages == null ? "<%color_accent%>%icon_hit% <white>%amount%" : messages.raw(key);
        return DisplayedText.mini(plugin, null, template, values);
    }

    private String legacy(String key, Map<String, String> values) {
        return TextLines.render(plugin, key, "&e%amount%", values);
    }

    private static Map<String, String> values(long hundredths, double remaining, long combo, String weapon) {
        return Map.of(
                "amount", DamageMath.format(hundredths),
                "combo", DamageMath.format(combo),
                "health", trim(remaining),
                "weapon", weapon == null ? "" : weapon
        );
    }

    private static String trim(double health) {
        if (Double.isNaN(health) || health < 0) {
            return "0";
        }
        return DamageMath.format(Math.round(health * 100.0));
    }

    private static long hundredths(Map<String, String> values) {
        try {
            return Math.round(Double.parseDouble(values.get("amount")) * 100.0);
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private static double remaining(Map<String, String> values) {
        try {
            return Double.parseDouble(values.get("health"));
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static long combo(Map<String, String> values) {
        try {
            return Math.round(Double.parseDouble(values.get("combo")) * 100.0);
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private static boolean online(Player player) {
        try {
            return player != null && player.isOnline();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static final class Live {
        private final Entity entity;
        private long amount;
        private long shownAt;
        private int generation = 1;

        private Live(Entity entity, long amount, long shownAt) {
            this.entity = entity;
            this.amount = amount;
            this.shownAt = shownAt;
        }
    }
}
