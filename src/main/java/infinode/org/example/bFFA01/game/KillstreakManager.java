package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.Compat;
import infinode.org.example.bFFA01.compat.Effects;
import infinode.org.example.bFFA01.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Killstreak tracking and rewards.
 *
 * <p>4.x hard-coded three rewards in Java. Here every reward comes from the
 * {@code killstreaks} section of {@code config.yml}, so server owners can tune
 * them without recompiling.
 */
public final class KillstreakManager {

    private final BFFA01 plugin;
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private final Map<Integer, Reward> rewards = new TreeMap<>();
    private int broadcastEvery = 5;

    public KillstreakManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    /** A configured reward for reaching a specific streak. */
    public record Reward(int streak, String message, boolean broadcast, String sound,
                         int coins, List<String> effects, List<String> items) {
    }

    /** Re-reads the {@code killstreaks} section. */
    public void reload() {
        rewards.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("killstreaks");
        if (section == null) {
            return;
        }
        broadcastEvery = Math.max(0, section.getInt("broadcast-every", 5));

        for (String key : section.getKeys(false)) {
            int streak;
            try {
                streak = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                // "broadcast-every" and any other option key.
                continue;
            }
            ConfigurationSection rewardSection = section.getConfigurationSection(key);
            if (rewardSection == null || streak <= 0) {
                continue;
            }
            rewards.put(streak, new Reward(
                    streak,
                    rewardSection.getString("message", ""),
                    rewardSection.getBoolean("broadcast", false),
                    rewardSection.getString("sound", "entity.player.levelup"),
                    Math.max(0, rewardSection.getInt("coins", 0)),
                    rewardSection.getStringList("effects"),
                    rewardSection.getStringList("items")));
        }
    }

    public int streak(Player player) {
        return player == null ? 0 : streaks.getOrDefault(player.getUniqueId(), 0);
    }

    /** Increments the killer's streak and grants any reward it unlocked. */
    public void addKill(Player killer) {
        if (killer == null) {
            return;
        }
        UUID uuid = killer.getUniqueId();
        int streak = streaks.merge(uuid, 1, Integer::sum);

        PlayerData data = plugin.dataManager().get(killer);
        if (streak > data.bestStreak()) {
            data.bestStreak(streak);
            plugin.dataManager().markDirty();
        }

        Reward reward = rewards.get(streak);
        if (reward != null) {
            grant(killer, reward);
        }
        boolean announced = reward != null && reward.broadcast();
        if (!announced && broadcastEvery > 0 && streak >= broadcastEvery && streak % broadcastEvery == 0) {
            plugin.messages().broadcast("killstreak-broadcast", "player", killer.getName(), "streak", streak);
        }
    }

    private void grant(Player player, Reward reward) {
        if (!reward.message().isEmpty()) {
            plugin.messages().send(player, "killstreak-reward",
                    "streak", reward.streak(),
                    "reward", infinode.org.example.bFFA01.util.Text.colorize(reward.message()));
        }
        if (reward.broadcast()) {
            plugin.messages().broadcast("killstreak-broadcast",
                    "player", player.getName(), "streak", reward.streak());
        }
        if (reward.coins() > 0) {
            plugin.dataManager().get(player).addCoins(reward.coins());
            plugin.dataManager().markDirty();
        }
        Compat.playSound(player, reward.sound(), 1.0F, 1.2F);

        for (PotionEffectSpec spec : parseEffects(reward.effects())) {
            Compat.applyEffect(player, spec.type(), spec.ticks(), spec.amplifier());
        }
        for (ItemStack item : parseItems(reward.items())) {
            player.getInventory().addItem(item);
        }
    }

    private record PotionEffectSpec(PotionEffectType type, int ticks, int amplifier) {
    }

    /** Parses entries in the form {@code speed:200:1}. */
    private List<PotionEffectSpec> parseEffects(List<String> raw) {
        List<PotionEffectSpec> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            String[] parts = entry.split(":");
            PotionEffectType type = Effects.byName(parts[0]);
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect '" + parts[0] + "' in a killstreak reward.");
                continue;
            }
            int ticks = parts.length > 1 ? parseInt(parts[1], 200) : 200;
            int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
            result.add(new PotionEffectSpec(type, ticks, amplifier));
        }
        return result;
    }

    /** Parses entries in the form {@code COBWEB:5}. */
    private List<ItemStack> parseItems(List<String> raw) {
        List<ItemStack> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            String[] parts = entry.split(":");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || material == Material.AIR) {
                plugin.getLogger().warning("Unknown material '" + parts[0] + "' in a killstreak reward.");
                continue;
            }
            int amount = parts.length > 1 ? Math.max(1, parseInt(parts[1], 1)) : 1;
            result.add(new ItemStack(material, amount));
        }
        return result;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Ends a player's streak.
     *
     * @param killer the player who ended it, may be {@code null}
     */
    public void resetStreak(Player victim, Player killer) {
        if (victim == null) {
            return;
        }
        Integer streak = streaks.remove(victim.getUniqueId());
        if (streak == null || broadcastEvery <= 0 || streak < broadcastEvery) {
            return;
        }
        plugin.messages().broadcast("killstreak-ended",
                "player", victim.getName(),
                "streak", streak,
                "killer", killer == null ? "-" : killer.getName());
    }

    public void clear(UUID uuid) {
        streaks.remove(uuid);
    }
}
