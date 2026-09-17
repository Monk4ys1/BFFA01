package infinode.org.example.bFFA01.shop;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The consumable items the shop sells.
 *
 * <p>Items carry their id in the persistent data container, so listeners
 * recognise them even after an admin renamed them in the config or a player put
 * them through an anvil. 4.x compared display names, which broke as soon as the
 * name in {@code ShopCommand} and the name in {@code GameListener} drifted
 * apart, and the fireball was priced 60 in the menu but charged 30 on purchase.
 */
public enum SpecialItem {

    GOLDEN_APPLE("golden_apple", Material.GOLDEN_APPLE, "&d1x Golden Apple", 10, 28,
            List.of("&7A quick heal in the middle of a fight.")),
    ENDER_PEARL("ender_pearl", Material.ENDER_PEARL, "&d1x Ender Pearl", 25, 29,
            List.of("&7Throw it to teleport.")),
    GRAPPLING_HOOK("grappling_hook", Material.FISHING_ROD, "&dGrappling Hook", 50, 30,
            List.of("&7Cast it and get pulled towards the hook.")),
    FIREBALL("fireball", Material.FIRE_CHARGE, "&cKnockback Fireball", 60, 31,
            List.of("&7Right-click to launch.", "&7Huge knockback, no damage.")),
    JUMP_FEATHER("jump_feather", Material.FEATHER, "&eJump Boost Feather", 20, 32,
            List.of("&7Right-click for Jump Boost III (10s).")),
    SPEED_POWDER("speed_powder", Material.SUGAR, "&fSpeed Powder", 20, 33,
            List.of("&7Right-click for Speed III (5s).")),
    WEB_GRENADE("web_grenade", Material.COBWEB, "&7Web Grenade", 40, 34,
            List.of("&7Throw it to trap whoever is chasing you.")),
    TRACKER("tracker", Material.COMPASS, "&aPlayer Tracker", 15, 37,
            List.of("&7Right-click to point at the nearest player.")),
    INVISIBILITY_CLOAK("invisibility_cloak", Material.GLASS, "&8Invisibility Cloak", 60, 38,
            List.of("&7Right-click for 15s of invisibility.", "&8Armour stays visible.")),
    KNOCKBACK_TNT("knockback_tnt", Material.TNT, "&cKnockback TNT", 40, 39,
            List.of("&7Right-click to throw a primed charge.")),
    RESCUE_PLATFORM("rescue_platform", Material.SLIME_BLOCK, "&bRescue Platform", 35, 40,
            List.of("&7Right-click to place a 3x3 platform below you.")),
    VAMPIRE_FANG("vampire_fang", Material.GHAST_TEAR, "&4Vampire Fang", 50, 41,
            List.of("&7Your next hit heals you completely.")),
    SWITCHER_BALL("switcher_ball", Material.SNOWBALL, "&dSwitcher Ball", 45, 42,
            List.of("&7Hit a player to swap places with them."));

    private final String id;
    private final Material material;
    private final String displayName;
    private final int defaultPrice;
    private final int defaultSlot;
    private final List<String> defaultLore;

    SpecialItem(String id, Material material, String displayName,
                int defaultPrice, int defaultSlot, List<String> defaultLore) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.defaultPrice = defaultPrice;
        this.defaultSlot = defaultSlot;
        this.defaultLore = defaultLore;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultPrice() {
        return defaultPrice;
    }

    public int defaultSlot() {
        return defaultSlot;
    }

    public List<String> defaultLore() {
        return defaultLore;
    }

    public static SpecialItem byId(String id) {
        if (id == null) {
            return null;
        }
        String normalised = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(item -> item.id.equals(normalised))
                .findFirst()
                .orElse(null);
    }
}
