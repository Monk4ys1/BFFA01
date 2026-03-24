package infinode.org.example.bFFA01.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillstreakManager {
    private final Map<UUID, Integer> streaks = new HashMap<>();

    public void addKill(Player player) {
        UUID uuid = player.getUniqueId();
        int streak = streaks.getOrDefault(uuid, 0) + 1;
        streaks.put(uuid, streak);

        if (streak == 3) {
            player.sendMessage(ChatColor.AQUA + "Killstreak: 3! You received Speed II!");
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 1));
        } else if (streak == 5) {
            player.sendMessage(ChatColor.AQUA + "Killstreak: 5! You received 5 Cobwebs!");
            player.getInventory().addItem(new ItemStack(Material.COBWEB, 5));
        } else if (streak == 10) {
            player.sendMessage(ChatColor.AQUA + "Killstreak: 10! You received a Golden Apple!");
            player.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
            player.getServer().broadcastMessage(ChatColor.GOLD + player.getName() + " is on a 10 Killstreak!");
        } else if (streak > 3 && streak % 5 == 0) {
            player.sendMessage(ChatColor.AQUA + "Killstreak: " + streak + "!");
            player.getServer().broadcastMessage(ChatColor.GOLD + player.getName() + " is on a " + streak + " Killstreak!");
        }
    }

    public void resetStreak(Player player) {
        UUID uuid = player.getUniqueId();
        int streak = streaks.getOrDefault(uuid, 0);
        if (streak >= 5) {
            player.getServer().broadcastMessage(ChatColor.RED + player.getName() + "'s Killstreak of " + streak + " was ruined!");
        }
        streaks.put(uuid, 0);
    }
}
