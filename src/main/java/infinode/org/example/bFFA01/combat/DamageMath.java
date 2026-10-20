package infinode.org.example.bFFA01.combat;

import java.util.Locale;

/**
 * Health is stored as hundredths of a point so assists and leaderboards do not drift.
 * A hit counts the health it actually removes, plus absorption it consumes.
 * Damage past the target's remaining health is overkill and is not counted.
 */
public final class DamageMath {

    private DamageMath() {
    }

    public static long hundredths(double finalDamage, double health, double absorptionTaken) {
        double damage = finite(finalDamage);
        double remaining = finite(health);
        double absorbed = finite(absorptionTaken);
        double applied = Math.min(damage, remaining);
        double total = applied + absorbed;
        if (total > 100_000.0) {
            total = 100_000.0;
        }
        return Math.round(total * 100.0);
    }

    public static double hearts(long hundredths) {
        return Math.max(0L, hundredths) / 100.0;
    }

    public static String format(long hundredths) {
        long scaled = Math.max(0L, hundredths);
        long whole = scaled / 100L;
        long frac = scaled % 100L;
        if (frac == 0L) {
            return Long.toString(whole);
        }
        if (frac % 10L == 0L) {
            return whole + "." + (frac / 10L);
        }
        return String.format(Locale.US, "%d.%02d", whole, frac);
    }

    private static double finite(double value) {
        if (Double.isNaN(value) || value < 0.0 || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }
}
