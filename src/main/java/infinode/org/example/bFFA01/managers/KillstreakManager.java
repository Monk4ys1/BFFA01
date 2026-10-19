package infinode.org.example.bFFA01.managers;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.compat.PotionEffects;
import infinode.org.example.bFFA01.compat.ServerScheduler;
import infinode.org.example.bFFA01.text.TextLines;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillstreakManager {
    private final BFFA01 plugin;
    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

    public KillstreakManager(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        int streak = streaks.getOrDefault(uuid, 0) + 1;
        streaks.put(uuid, streak);

        if (streak == 3) {
            tell(player, "killstreak-3", "&bKillstreak: 3! You received Speed II!", Map.of());
            PotionEffectType speed = PotionEffects.speed();
            if (speed != null) {
                player.addPotionEffect(new PotionEffect(speed, 20 * 10, 1));
            }
        } else if (streak == 5) {
            tell(player, "killstreak-5", "&bKillstreak: 5! You received 5 Cobwebs!", Map.of());
            player.getInventory().addItem(new ItemStack(Material.COBWEB, 5));
        } else if (streak == 10) {
            tell(player, "killstreak-10", "&bKillstreak: 10! You received a Golden Apple!", Map.of());
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            announce("killstreak-broadcast", "&6%player% is on a 10 Killstreak!",
                    Map.of("player", player.getName(), "streak", Integer.toString(streak)));
        } else if (streak > 3 && streak % 5 == 0) {
            tell(player, "killstreak-generic", "&bKillstreak: %streak%!", Map.of("streak", Integer.toString(streak)));
            announce("killstreak-broadcast", "&6%player% is on a %streak% Killstreak!",
                    Map.of("player", player.getName(), "streak", Integer.toString(streak)));
        }
        if (plugin.presentation() != null) {
            plugin.presentation().onStreak(player, streak);
        }
    }

    public void resetStreak(Player player) {
        UUID uuid = player.getUniqueId();
        int streak = streaks.getOrDefault(uuid, 0);
        if (streak >= 5) {
            announce("killstreak-ruined", "&c%player%'s Killstreak of %streak% was ruined!",
                    Map.of("player", player.getName(), "streak", Integer.toString(streak)));
        }
        streaks.put(uuid, 0);
    }

    public int getStreak(Player player) {
        return streaks.getOrDefault(player.getUniqueId(), 0);
    }

    private void tell(Player player, String key, String fallback, Map<String, String> values) {
        player.sendMessage(TextLines.render(plugin, key, fallback, values));
    }

    private void announce(String key, String fallback, Map<String, String> values) {
        String message = TextLines.render(plugin, key, fallback, values);
        ServerScheduler scheduler = plugin.scheduler();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (scheduler == null) {
                online.sendMessage(message);
            } else {
                scheduler.runOnEntity(online, () -> online.sendMessage(message));
            }
        }
    }
}
