package infinode.org.example.bFFA01.compat;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime bridge over the API changes between Minecraft 1.20.1 and current
 * releases. Everything in here is resolved reflectively and cached, so the same
 * jar links against whichever variant the running server ships.
 *
 * <p>The differences this class hides:
 * <ul>
 *   <li>{@code Enchantment} constants were renamed in 1.20.5
 *       ({@code DAMAGE_ALL} &rarr; {@code SHARPNESS}, ...).</li>
 *   <li>{@code PotionEffectType} constants were renamed in 1.20.5
 *       ({@code JUMP} &rarr; {@code JUMP_BOOST}, ...).</li>
 *   <li>{@code Particle} constants were renamed in 1.20.5
 *       ({@code EXPLOSION_NORMAL} &rarr; {@code EXPLOSION}, ...).</li>
 *   <li>The max-health attribute key moved from {@code generic.max_health}
 *       to {@code max_health} in 1.21.2.</li>
 *   <li>{@code Player#respawn()} only exists on newer servers.</li>
 * </ul>
 *
 * <p>Two further incompatibilities are avoided by design rather than bridged:
 * inventories are identified through an {@code InventoryHolder} instead of
 * {@code InventoryView#getTitle()} (which breaks because {@code InventoryView}
 * became an interface in 1.21), and sounds are played through the
 * {@code String} overload so no {@code Sound} constant is ever linked.
 */
public final class Compat {

    private static final Map<String, Enchantment> ENCHANTMENTS = new ConcurrentHashMap<>();
    private static final Map<String, PotionEffectType> EFFECTS = new ConcurrentHashMap<>();
    private static final Map<String, Particle> PARTICLES = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new HashMap<>();

    /** Sentinel so a failed lookup is cached instead of retried forever. */
    private static final Method NO_METHOD;

    static {
        Method sentinel = null;
        try {
            sentinel = Compat.class.getDeclaredMethod("noop");
        } catch (NoSuchMethodException ignored) {
            // Cannot happen: noop() is declared below.
        }
        NO_METHOD = sentinel;
    }

    private Compat() {
    }

    @SuppressWarnings("unused")
    private static void noop() {
        // Placeholder target for NO_METHOD.
    }

    // ------------------------------------------------------------------
    // Registry lookups
    // ------------------------------------------------------------------

    /**
     * Resolves an enchantment by its (version stable) Minecraft key, falling
     * back to the API constant names that changed across versions.
     *
     * @param key           vanilla registry key, e.g. {@code sharpness}
     * @param constantNames API field names to try, newest first
     */
    public static Enchantment enchantment(String key, String... constantNames) {
        Enchantment cached = ENCHANTMENTS.get(key);
        if (cached != null) {
            return cached;
        }
        Enchantment resolved = resolve(Enchantment.class, "ENCHANTMENT", key, constantNames);
        if (resolved != null) {
            ENCHANTMENTS.put(key, resolved);
        }
        return resolved;
    }

    /**
     * Resolves a potion effect type by its vanilla key.
     *
     * @param key           vanilla registry key, e.g. {@code jump_boost}
     * @param constantNames API field names to try, newest first
     */
    public static PotionEffectType potionEffect(String key, String... constantNames) {
        PotionEffectType cached = EFFECTS.get(key);
        if (cached != null) {
            return cached;
        }
        PotionEffectType resolved = resolve(PotionEffectType.class, "EFFECT", key, constantNames);
        if (resolved == null) {
            resolved = resolve(PotionEffectType.class, "MOB_EFFECT", key, constantNames);
        }
        if (resolved == null) {
            resolved = resolve(PotionEffectType.class, "POTION_EFFECT_TYPE", key, constantNames);
        }
        if (resolved != null) {
            EFFECTS.put(key, resolved);
        }
        return resolved;
    }

    /**
     * Resolves a particle from a list of candidate constant names, newest
     * naming first. Returns {@code null} when none of them exist, callers are
     * expected to treat particles as cosmetic and skip them.
     */
    public static Particle particle(String... constantNames) {
        String cacheKey = String.join("|", constantNames);
        Particle cached = PARTICLES.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        for (String name : constantNames) {
            Object value = staticField(Particle.class, name);
            if (value instanceof Particle particle) {
                PARTICLES.put(cacheKey, particle);
                return particle;
            }
        }
        return null;
    }

    /**
     * Generic three-step lookup: {@code Registry.X.get(key)}, then
     * {@code Type.getByKey(key)}, then the static constants.
     */
    private static <T> T resolve(Class<T> type, String registryField, String key, String... constantNames) {
        Object viaRegistry = fromRegistry(registryField, key);
        if (type.isInstance(viaRegistry)) {
            return type.cast(viaRegistry);
        }
        Object viaKey = invokeStatic(type, "getByKey", NamespacedKey.class, minecraftKey(key));
        if (type.isInstance(viaKey)) {
            return type.cast(viaKey);
        }
        for (String name : constantNames) {
            Object value = staticField(type, name);
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return null;
    }

    private static Object fromRegistry(String registryField, String key) {
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Object registry = registryClass.getField(registryField).get(null);
            if (registry == null) {
                return null;
            }
            // Look the method up on the public interface: the implementation
            // class behind the field is not necessarily accessible.
            Method get = registryClass.getMethod("get", NamespacedKey.class);
            return get.invoke(registry, minecraftKey(key));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NamespacedKey minecraftKey(String key) {
        return NamespacedKey.minecraft(key.toLowerCase(java.util.Locale.ROOT));
    }

    private static Object staticField(Class<?> owner, String name) {
        try {
            return owner.getField(name).get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeStatic(Class<?> owner, String name, Class<?> paramType, Object argument) {
        Method method = lookup(owner, name, paramType);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, argument);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized Method lookup(Class<?> owner, String name, Class<?>... paramTypes) {
        StringBuilder cacheKey = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> paramType : paramTypes) {
            cacheKey.append(';').append(paramType.getName());
        }
        Method cached = METHOD_CACHE.get(cacheKey.toString());
        if (cached != null) {
            return cached == NO_METHOD ? null : cached;
        }
        Method found = null;
        try {
            found = owner.getMethod(name, paramTypes);
        } catch (Throwable ignored) {
            // Not present on this server version.
        }
        METHOD_CACHE.put(cacheKey.toString(), found == null ? NO_METHOD : found);
        return found;
    }

    // ------------------------------------------------------------------
    // Player helpers
    // ------------------------------------------------------------------

    /**
     * Plays a sound by its vanilla key. The {@code String} overload of
     * {@code playSound} exists on every supported version, so no {@code Sound}
     * constant (which became an interface in 1.21.3) is ever linked.
     */
    public static void playSound(Player player, String soundKey, float volume, float pitch) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), soundKey, volume, pitch);
        } catch (Throwable ignored) {
            // Unknown sound key on this version: cosmetic only.
        }
    }

    /** Plays a sound at a location for everyone in range. */
    public static void playSound(Location location, String soundKey, float volume, float pitch) {
        if (location == null || location.getWorld() == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        try {
            location.getWorld().playSound(location, soundKey, volume, pitch);
        } catch (Throwable ignored) {
            // Unknown sound key on this version: cosmetic only.
        }
    }

    /** Current maximum health, using the attribute when available. */
    public static double maxHealth(Player player) {
        Double fromAttribute = maxHealthFromAttribute(player);
        if (fromAttribute != null && fromAttribute > 0.0D) {
            return fromAttribute;
        }
        try {
            return player.getMaxHealth();
        } catch (Throwable ignored) {
            return 20.0D;
        }
    }

    private static Double maxHealthFromAttribute(Player player) {
        // The attribute key was renamed from "generic.max_health" to
        // "max_health" in 1.21.2; try both and give up quietly.
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            Object attribute = fromRegistry("ATTRIBUTE", key);
            if (attribute == null) {
                continue;
            }
            try {
                Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
                Class<?> instanceClass = Class.forName("org.bukkit.attribute.AttributeInstance");
                // Resolve against the public interfaces: the CraftBukkit
                // implementation classes are not accessible from a plugin.
                Object instance = Player.class.getMethod("getAttribute", attributeClass).invoke(player, attribute);
                if (instance == null) {
                    continue;
                }
                Object value = instanceClass.getMethod("getValue").invoke(instance);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (Throwable ignored) {
                // Fall through to the next key / the deprecated getter.
            }
        }
        return null;
    }

    /** Restores a player to full health. */
    public static void heal(Player player) {
        if (player == null) {
            return;
        }
        try {
            player.setHealth(Math.max(1.0D, maxHealth(player)));
        } catch (Throwable ignored) {
            // Player died in the same tick.
        }
    }

    /** Forces a respawn, preferring the modern API over the Spigot shim. */
    public static void respawn(Player player) {
        if (player == null || !player.isDead()) {
            return;
        }
        Method modern = lookup(Player.class, "respawn");
        if (modern != null) {
            try {
                modern.invoke(player);
                return;
            } catch (Throwable ignored) {
                // Fall through to the Spigot shim.
            }
        }
        try {
            player.spigot().respawn();
        } catch (Throwable ignored) {
            // Nothing else we can do; the player respawns manually.
        }
    }

    /** Applies a potion effect, silently skipping unknown effect types. */
    public static void applyEffect(Player player, PotionEffectType type, int ticks, int amplifier) {
        if (player == null || type == null) {
            return;
        }
        try {
            player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, false, true));
        } catch (Throwable ignored) {
            // Invalid effect parameters on this version.
        }
    }
}
