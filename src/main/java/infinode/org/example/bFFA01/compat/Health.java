package infinode.org.example.bFFA01.compat;

import org.bukkit.entity.Player;

public final class Health {

    private Health() {
    }

    public static double max(Player player) {
        if (player == null) {
            return 20.0;
        }
        try {
            double value = player.getMaxHealth();
            return value > 0.0 ? value : 20.0;
        } catch (LinkageError error) {
            return 20.0;
        }
    }

    public static void fill(Player player) {
        if (player == null || player.isDead()) {
            return;
        }
        player.setHealth(max(player));
    }
}
