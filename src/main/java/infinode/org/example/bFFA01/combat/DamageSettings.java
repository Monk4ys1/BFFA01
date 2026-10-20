package infinode.org.example.bFFA01.combat;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Global damage tracking and the live channels from presentation.yml.
 * Missing keys keep the defaults so an older config.yml still works.
 */
public final class DamageSettings {

    private final boolean enabled;
    private final long assistMillis;
    private final long minimumHundredths;
    private final boolean indicators;
    private final boolean actionBar;
    private final boolean summary;
    private final int lifetimeTicks;
    private final long mergeMillis;

    private DamageSettings(boolean enabled, long assistMillis, long minimumHundredths, boolean indicators,
                           boolean actionBar, boolean summary, int lifetimeTicks, long mergeMillis) {
        this.enabled = enabled;
        this.assistMillis = assistMillis;
        this.minimumHundredths = minimumHundredths;
        this.indicators = indicators;
        this.actionBar = actionBar;
        this.summary = summary;
        this.lifetimeTicks = lifetimeTicks;
        this.mergeMillis = mergeMillis;
    }

    public static DamageSettings load(FileConfiguration config, FileConfiguration presentation) {
        boolean enabled = config == null || config.getBoolean("damage.enabled", true);
        double seconds = config == null ? 12.0 : config.getDouble("damage.assist-seconds", 12.0);
        if (seconds < 1.0) {
            seconds = 1.0;
        } else if (seconds > 60.0) {
            seconds = 60.0;
        }
        double minimum = config == null ? 2.0 : config.getDouble("damage.assist-minimum", 2.0);
        if (minimum < 0.0 || Double.isNaN(minimum)) {
            minimum = 0.0;
        } else if (minimum > 1000.0) {
            minimum = 1000.0;
        }
        boolean indicators = presentation == null || presentation.getBoolean("damage.indicators", true);
        boolean actionBar = presentation == null || presentation.getBoolean("damage.actionbar", true);
        boolean summary = presentation == null || presentation.getBoolean("damage.summary", true);
        int ticks = presentation == null ? 18 : presentation.getInt("damage.ticks", 18);
        if (ticks < 8) {
            ticks = 8;
        } else if (ticks > 40) {
            ticks = 40;
        }
        long merge = presentation == null ? 150L : presentation.getLong("damage.merge-millis", 150L);
        if (merge < 0L) {
            merge = 0L;
        } else if (merge > 1000L) {
            merge = 1000L;
        }
        return new DamageSettings(enabled, Math.round(seconds * 1000.0), Math.round(minimum * 100.0),
                indicators, actionBar, summary, ticks, merge);
    }

    public boolean enabled() {
        return enabled;
    }

    public long assistMillis() {
        return assistMillis;
    }

    public long minimumHundredths() {
        return minimumHundredths;
    }

    public boolean indicators() {
        return indicators;
    }

    public boolean actionBar() {
        return actionBar;
    }

    public boolean summary() {
        return summary;
    }

    public int lifetimeTicks() {
        return lifetimeTicks;
    }

    public long mergeMillis() {
        return mergeMillis;
    }
}
