package infinode.org.example.bFFA01.game;

import infinode.org.example.bFFA01.config.Settings;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/**
 * One configured BuildFFA arena: a spawn point plus the two Y levels that
 * define where players die and where they are safe.
 */
public final class Arena {

    private final String id;
    private final String displayName;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final int deathY;
    private final int safezoneY;

    private Arena(String id, String displayName, String worldName,
                  double x, double y, double z, float yaw, float pitch,
                  int deathY, int safezoneY) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.deathY = deathY;
        this.safezoneY = safezoneY;
    }

    /** Reads an arena from {@code maps.yml}, filling gaps from the defaults. */
    public static Arena fromSection(String id, ConfigurationSection section, Settings settings) {
        if (section == null) {
            return null;
        }
        String world = section.getString("world");
        if (world == null || world.isEmpty()) {
            return null;
        }
        return new Arena(
                id,
                section.getString("display-name", Text.prettify(id)),
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"),
                section.getInt("death-y-level", settings.defaultDeathY()),
                section.getInt("safezone-y-level", settings.defaultSafezoneY()));
    }

    /** Creates an arena from the location an admin is standing at. */
    public static Arena fromLocation(String id, Location location, Settings settings) {
        World world = location.getWorld();
        return new Arena(
                id,
                Text.prettify(id),
                world == null ? "world" : world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                settings.defaultDeathY(),
                settings.defaultSafezoneY());
    }

    /** Returns a copy with new Y levels, used by {@code /bffa setlevel}. */
    public Arena withLevels(int newDeathY, int newSafezoneY) {
        return new Arena(id, displayName, worldName, x, y, z, yaw, pitch, newDeathY, newSafezoneY);
    }

    /** Writes this arena back into {@code maps.yml}. */
    public void write(ConfigurationSection root) {
        ConfigurationSection section = root.createSection("maps." + id);
        section.set("display-name", displayName);
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
        section.set("death-y-level", deathY);
        section.set("safezone-y-level", safezoneY);
    }

    /** Spawn location, or {@code null} when the world is not loaded. */
    public Location spawn() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldName) != null;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName == null || displayName.isEmpty() ? Text.prettify(id) : displayName;
    }

    public String worldName() {
        return worldName;
    }

    public int deathY() {
        return deathY;
    }

    public int safezoneY() {
        return safezoneY;
    }

    @Override
    public String toString() {
        return "Arena{" + id + " @ " + worldName + " " + Math.round(x) + "/" + Math.round(y) + "/" + Math.round(z) + "}";
    }
}
