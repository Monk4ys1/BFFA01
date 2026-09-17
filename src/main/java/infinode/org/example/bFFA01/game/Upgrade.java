package infinode.org.example.bFFA01.game;

import org.bukkit.Material;

import java.util.Locale;

/**
 * The permanent upgrades players buy in the shop.
 *
 * <p>The ids match the ones 4.x wrote into {@code data.yml}, so upgrades that
 * players already bought keep working after the update.
 */
public enum Upgrade {

    SHARPNESS("sharpness", Material.IRON_SWORD, "&b&lSharpness I", 100, Target.SWORD, "sharpness", 1, 10),
    PROTECTION("protection", Material.CHAINMAIL_CHESTPLATE, "&b&lProtection I", 150, Target.CHESTPLATE, "protection", 1, 11),
    POWER_BOW("powerbow", Material.BOW, "&b&lPower I", 100, Target.BOW, "power", 1, 12),
    KNOCKBACK_STICK("knockback2", Material.STICK, "&b&lKnockback II", 150, Target.STICK, "knockback", 2, 13),
    FEATHER_FALLING("featherfalling", Material.CHAINMAIL_BOOTS, "&b&lFeather Falling II", 120, Target.BOOTS, "feather_falling", 2, 14),
    PUNCH_BOW("punchbow", Material.ARROW, "&b&lPunch I", 120, Target.BOW, "punch", 1, 15);

    /** Which kit item an upgrade attaches to. */
    public enum Target {
        SWORD,
        BOW,
        STICK,
        CHESTPLATE,
        BOOTS;

        public boolean matches(Material material) {
            if (material == null) {
                return false;
            }
            String name = material.name();
            return switch (this) {
                case SWORD -> name.endsWith("_SWORD");
                case BOW -> material == Material.BOW || material == Material.CROSSBOW;
                case STICK -> material == Material.STICK || material == Material.BLAZE_ROD;
                case CHESTPLATE -> name.endsWith("_CHESTPLATE");
                case BOOTS -> name.endsWith("_BOOTS");
            };
        }
    }

    private final String id;
    private final Material icon;
    private final String displayName;
    private final int defaultPrice;
    private final Target target;
    private final String enchantmentKey;
    private final int level;
    private final int defaultSlot;

    Upgrade(String id, Material icon, String displayName, int defaultPrice,
            Target target, String enchantmentKey, int level, int defaultSlot) {
        this.id = id;
        this.icon = icon;
        this.displayName = displayName;
        this.defaultPrice = defaultPrice;
        this.target = target;
        this.enchantmentKey = enchantmentKey;
        this.level = level;
        this.defaultSlot = defaultSlot;
    }

    public String id() {
        return id;
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultPrice() {
        return defaultPrice;
    }

    public Target target() {
        return target;
    }

    public String enchantmentKey() {
        return enchantmentKey;
    }

    public int level() {
        return level;
    }

    public int defaultSlot() {
        return defaultSlot;
    }

    public static Upgrade byId(String id) {
        if (id == null) {
            return null;
        }
        String normalised = id.toLowerCase(Locale.ROOT);
        for (Upgrade upgrade : values()) {
            if (upgrade.id.equals(normalised)) {
                return upgrade;
            }
        }
        return null;
    }
}
