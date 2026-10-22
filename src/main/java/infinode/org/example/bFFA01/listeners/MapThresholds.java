package infinode.org.example.bFFA01.listeners;

import java.util.function.IntSupplier;

/**
 * Death and safezone heights for the current map. Movement and damage read these on every event,
 * so the config lookup runs again only when the map or a reload changes.
 */
public final class MapThresholds {

    private String map = "\0";
    private int stamp = Integer.MIN_VALUE;
    private int deathY = 50;
    private int safeY = 90;

    public synchronized int deathY(String mapName, int configStamp, IntSupplier death, IntSupplier safe) {
        refresh(mapName, configStamp, death, safe);
        return deathY;
    }

    public synchronized int safeY(String mapName, int configStamp, IntSupplier death, IntSupplier safe) {
        refresh(mapName, configStamp, death, safe);
        return safeY;
    }

    private void refresh(String mapName, int configStamp, IntSupplier death, IntSupplier safe) {
        String name = mapName == null ? "" : mapName;
        if (configStamp == stamp && name.equals(map)) {
            return;
        }
        deathY = death.getAsInt();
        safeY = safe.getAsInt();
        map = name;
        stamp = configStamp;
    }
}
