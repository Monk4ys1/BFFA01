package infinode.org.example.bFFA01.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Typed snapshot of {@code config.yml}.
 *
 * <p>The previous version read the configuration inside {@code PlayerMoveEvent}
 * and inside a two tick timer, which meant thousands of map lookups and string
 * concatenations per second. Everything is parsed once here and re-parsed only
 * on {@code /bffa reload}.
 */
public final class Settings {

    // Arena
    private int mapSwapInterval = 600;
    private Set<Integer> mapSwapWarnings = new TreeSet<>(Arrays.asList(60, 30, 10, 5, 3, 2, 1));
    private int resetBlocksBeforeSwap = 3;
    private int defaultDeathY = 50;
    private int defaultSafezoneY = 90;

    // Building
    private int blockRemoveDelay = 10;
    private int blockWarningSeconds = 2;
    private Material blockWarningMaterial = Material.REDSTONE_BLOCK;
    private boolean placedBlocksBreakable = true;

    // Combat
    private int combatTagSeconds = 10;
    private boolean blockCommandsInCombat = true;
    private Set<String> allowedCombatCommands = new HashSet<>();
    private boolean punishCombatLog = true;
    private boolean colourNames = true;

    // Rewards
    private int coinsPerKill = 5;
    private int coinsPerKillstreak = 1;
    private boolean healOnKill = true;
    private boolean refillArrowsOnKill = true;
    private int blocksPerKill = 16;

    // Protection
    private boolean cancelFallDamage = true;
    private boolean cancelExplosionDamage = true;
    private boolean disableHunger = true;
    private boolean disableDurability = true;
    private boolean preventItemDrop = true;
    private boolean preventOffhand = true;
    private boolean preventMobSpawns = true;

    // Display
    private boolean scoreboardEnabled = true;
    private boolean bossBarEnabled = true;
    private String bossBarTitle = "&7Next arena in &b%time%";
    private String bossBarColor = "BLUE";
    private boolean actionBarCombatTimer = true;
    private boolean tablistEnabled = true;
    private List<String> tablistHeader = new ArrayList<>();
    private List<String> tablistFooter = new ArrayList<>();
    private boolean hologramsEnabled = true;
    private int hologramEntries = 5;
    private double hologramOffsetY = 3.5D;
    private int hologramRefreshSeconds = 30;
    private boolean titlesEnabled = true;

    // Storage
    private int autosaveSeconds = 300;

    /** Re-reads every value. Old flat 4.x keys are still honoured as fallback. */
    public void reload(FileConfiguration config) {
        mapSwapInterval = Math.max(10, first(config, 60, "arena.map-swap-interval", "map-swap-interval"));
        mapSwapWarnings = toIntSet(config.getIntegerList("arena.map-swap-warnings"));
        if (mapSwapWarnings.isEmpty()) {
            mapSwapWarnings = new TreeSet<>(Arrays.asList(60, 30, 10, 5, 3, 2, 1));
        }
        resetBlocksBeforeSwap = clamp(config.getInt("arena.reset-blocks-before-swap", 3), 0, 60);
        defaultDeathY = first(config, 50, "arena.default-death-y-level", "default-death-y-level");
        defaultSafezoneY = first(config, 90, "arena.default-safezone-y-level", "default-safezone-y-level");

        blockRemoveDelay = Math.max(1, first(config, 10, "building.block-remove-delay", "block-remove-delay"));
        blockWarningSeconds = clamp(config.getInt("building.block-warning-seconds", 2), 0, blockRemoveDelay - 1);
        blockWarningMaterial = material(config.getString("building.warning-material"), Material.REDSTONE_BLOCK);
        placedBlocksBreakable = config.getBoolean("building.breakable-placed-blocks", true);

        combatTagSeconds = Math.max(0, first(config, 10, "combat.tag-seconds", "combat-regen-pause"));
        blockCommandsInCombat = config.getBoolean("combat.block-commands", true);
        allowedCombatCommands = lowerCaseSet(config.getStringList("combat.allowed-commands"));
        punishCombatLog = config.getBoolean("combat.punish-combat-log", true);
        colourNames = config.getBoolean("combat.colour-names", true);

        coinsPerKill = Math.max(0, config.getInt("rewards.coins-per-kill", 5));
        coinsPerKillstreak = Math.max(0, config.getInt("rewards.coins-per-killstreak", 1));
        healOnKill = config.getBoolean("rewards.heal-on-kill", true);
        refillArrowsOnKill = config.getBoolean("rewards.refill-arrows", true);
        blocksPerKill = Math.max(0, config.getInt("rewards.blocks-per-kill", 16));

        cancelFallDamage = config.getBoolean("protection.cancel-fall-damage", true);
        cancelExplosionDamage = config.getBoolean("protection.cancel-explosion-damage", true);
        disableHunger = config.getBoolean("protection.disable-hunger", true);
        disableDurability = config.getBoolean("protection.disable-durability", true);
        preventItemDrop = config.getBoolean("protection.prevent-item-drop", true);
        preventOffhand = config.getBoolean("protection.prevent-offhand", true);
        preventMobSpawns = config.getBoolean("protection.prevent-mob-spawns", true);

        scoreboardEnabled = config.getBoolean("display.scoreboard", true);
        bossBarEnabled = config.getBoolean("display.bossbar", true);
        bossBarTitle = config.getString("display.bossbar-title", "&7Next arena in &b%time%");
        bossBarColor = config.getString("display.bossbar-color", "BLUE");
        actionBarCombatTimer = config.getBoolean("display.actionbar-combat-timer", true);
        tablistEnabled = config.getBoolean("display.tablist", true);
        tablistHeader = config.getStringList("display.tablist-header");
        tablistFooter = config.getStringList("display.tablist-footer");
        hologramsEnabled = config.getBoolean("display.holograms", true);
        hologramEntries = clamp(config.getInt("display.hologram-entries", 5), 1, 15);
        hologramOffsetY = config.getDouble("display.hologram-offset-y", 3.5D);
        hologramRefreshSeconds = Math.max(5, config.getInt("display.hologram-refresh-seconds", 30));
        titlesEnabled = config.getBoolean("display.titles", true);

        autosaveSeconds = Math.max(30, config.getInt("storage.autosave-seconds", 300));
    }

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    /** Reads the first path that exists, so 4.x configs keep working. */
    private static int first(FileConfiguration config, int fallback, String... paths) {
        for (String path : paths) {
            // contains(path, true) ignores the bundled defaults, so a value the
            // admin actually wrote wins over the new default path.
            if (config.contains(path, true)) {
                return config.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Set<Integer> toIntSet(List<Integer> values) {
        Set<Integer> result = new TreeSet<>();
        if (values != null) {
            for (Integer value : values) {
                if (value != null && value > 0) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static Set<String> lowerCaseSet(List<String> values) {
        Set<String> result = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isEmpty()) {
                    result.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static Material material(String name, Material fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(name);
        return parsed != null ? parsed : fallback;
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public int mapSwapInterval() {
        return mapSwapInterval;
    }

    public Set<Integer> mapSwapWarnings() {
        return mapSwapWarnings;
    }

    public int resetBlocksBeforeSwap() {
        return resetBlocksBeforeSwap;
    }

    public int defaultDeathY() {
        return defaultDeathY;
    }

    public int defaultSafezoneY() {
        return defaultSafezoneY;
    }

    public int blockRemoveDelay() {
        return blockRemoveDelay;
    }

    public int blockWarningSeconds() {
        return blockWarningSeconds;
    }

    public Material blockWarningMaterial() {
        return blockWarningMaterial;
    }

    public boolean placedBlocksBreakable() {
        return placedBlocksBreakable;
    }

    public int combatTagSeconds() {
        return combatTagSeconds;
    }

    public long combatTagMillis() {
        return combatTagSeconds * 1000L;
    }

    public boolean blockCommandsInCombat() {
        return blockCommandsInCombat;
    }

    public boolean isCommandAllowedInCombat(String command) {
        return allowedCombatCommands.contains(command.toLowerCase(Locale.ROOT));
    }

    public boolean punishCombatLog() {
        return punishCombatLog;
    }

    public boolean colourNames() {
        return colourNames;
    }

    public int coinsPerKill() {
        return coinsPerKill;
    }

    public int coinsPerKillstreak() {
        return coinsPerKillstreak;
    }

    public boolean healOnKill() {
        return healOnKill;
    }

    public boolean refillArrowsOnKill() {
        return refillArrowsOnKill;
    }

    public int blocksPerKill() {
        return blocksPerKill;
    }

    public boolean cancelFallDamage() {
        return cancelFallDamage;
    }

    public boolean cancelExplosionDamage() {
        return cancelExplosionDamage;
    }

    public boolean disableHunger() {
        return disableHunger;
    }

    public boolean disableDurability() {
        return disableDurability;
    }

    public boolean preventItemDrop() {
        return preventItemDrop;
    }

    public boolean preventOffhand() {
        return preventOffhand;
    }

    public boolean preventMobSpawns() {
        return preventMobSpawns;
    }

    public boolean scoreboardEnabled() {
        return scoreboardEnabled;
    }

    public boolean bossBarEnabled() {
        return bossBarEnabled;
    }

    public String bossBarTitle() {
        return bossBarTitle;
    }

    public String bossBarColor() {
        return bossBarColor;
    }

    public boolean actionBarCombatTimer() {
        return actionBarCombatTimer;
    }

    public boolean tablistEnabled() {
        return tablistEnabled;
    }

    public List<String> tablistHeader() {
        return tablistHeader;
    }

    public List<String> tablistFooter() {
        return tablistFooter;
    }

    public boolean hologramsEnabled() {
        return hologramsEnabled;
    }

    public int hologramEntries() {
        return hologramEntries;
    }

    public double hologramOffsetY() {
        return hologramOffsetY;
    }

    public int hologramRefreshSeconds() {
        return hologramRefreshSeconds;
    }

    public boolean titlesEnabled() {
        return titlesEnabled;
    }

    public int autosaveSeconds() {
        return autosaveSeconds;
    }
}
