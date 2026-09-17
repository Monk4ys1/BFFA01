package infinode.org.example.bFFA01.config;

import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central place for every player facing string.
 *
 * <p>Values are read once per reload and cached; a message that is missing from
 * the admin's config falls back to the built in default instead of printing
 * {@code null} in chat. A message set to an empty string is suppressed, which
 * is how join/quit announcements are turned off.
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("prefix", "&8[&b&lBuildFFA&8] &r");
        DEFAULTS.put("no-permission", "&cYou are not allowed to do that.");
        DEFAULTS.put("player-only", "&cThis command can only be used in game.");
        DEFAULTS.put("player-not-found", "&cNo player named &e%player% &cis online.");
        DEFAULTS.put("invalid-number", "&c'&e%input%&c' is not a valid number.");
        DEFAULTS.put("join", "&8[&a&l+&8] &7%player%");
        DEFAULTS.put("quit", "&8[&c&l-&8] &7%player%");
        DEFAULTS.put("map-swapped", "&7The arena is now &b%map%&7.");
        DEFAULTS.put("map-swap-warning", "&7Next arena in &b%seconds%&7s.");
        DEFAULTS.put("map-swap-title", "&b&l%map%");
        DEFAULTS.put("map-swap-subtitle", "&7A new arena has started");
        DEFAULTS.put("map-set", "&aSaved the spawn of arena &e%map%&a.");
        DEFAULTS.put("map-deleted", "&aDeleted arena &e%map%&a.");
        DEFAULTS.put("map-unknown", "&cThere is no arena called &e%map%&c.");
        DEFAULTS.put("map-none", "&cNo arena is configured yet. Use &e/bffa setmap <name>&c.");
        DEFAULTS.put("map-list", "&7Arenas &8(&b%count%&8)&7: &f%maps%");
        DEFAULTS.put("player-killed", "&c%player% &7was killed by &a%killer%&7.");
        DEFAULTS.put("player-died", "&c%player% &7died.");
        DEFAULTS.put("kill-reward", "&7+&6%coins% coins &8| &7Streak &d%streak%");
        DEFAULTS.put("combat-log", "&c%player% &7left while in combat &8(&a%killer% &7got the kill&8)");
        DEFAULTS.put("combat-command-blocked", "&cYou cannot use commands while in combat.");
        DEFAULTS.put("combat-actionbar", "&cIn combat &8| %bar% &c%seconds%s");
        DEFAULTS.put("combat-tagged", "&cYou are now in combat!");
        DEFAULTS.put("build-blocked-safezone", "&cYou cannot build inside the safe zone.");
        DEFAULTS.put("build-blocked-swap", "&cThe arena is being reset right now.");
        DEFAULTS.put("build-break-blocked", "&cYou can only break blocks that players placed.");
        DEFAULTS.put("build-mode-on", "&aBuild mode enabled. Arena protection is off for you.");
        DEFAULTS.put("build-mode-off", "&cBuild mode disabled.");
        DEFAULTS.put("build-mode-other-on", "&aEnabled build mode for &e%player%&a.");
        DEFAULTS.put("build-mode-other-off", "&cDisabled build mode for &e%player%&c.");
        DEFAULTS.put("shop-not-enough-coins", "&cYou need &6%missing% &cmore coins for that.");
        DEFAULTS.put("shop-purchased", "&aPurchased &f%item%&a for &6%price% coins&a.");
        DEFAULTS.put("shop-already-owned", "&cYou already own that upgrade.");
        DEFAULTS.put("shop-inventory-full", "&cYour inventory is full.");
        DEFAULTS.put("shop-blocked-in-combat", "&cYou cannot open the shop while in combat.");
        DEFAULTS.put("kit-saved", "&aYour kit layout has been saved.");
        DEFAULTS.put("kit-reset", "&aYour kit layout has been reset to the default.");
        DEFAULTS.put("kit-editor-hint", "&7Click two items to swap them, then press &aSave&7.");
        DEFAULTS.put("killstreak-broadcast", "&6%player% &7is on a &e%streak% &7killstreak!");
        DEFAULTS.put("killstreak-ended", "&7%player%&7's streak of &e%streak% &7was ended by &c%killer%&7.");
        DEFAULTS.put("killstreak-reward", "&bKillstreak %streak%! &7%reward%");
        DEFAULTS.put("coins-given", "&aGave &6%amount% coins &ato &e%player%&a.");
        DEFAULTS.put("coins-taken", "&aTook &6%amount% coins &afrom &e%player%&a.");
        DEFAULTS.put("coins-set", "&aSet the balance of &e%player% &ato &6%amount% coins&a.");
        DEFAULTS.put("coins-received", "&aYou received &6%amount% coins&a.");
        DEFAULTS.put("stats-reset", "&aReset the statistics of &e%player%&a.");
        DEFAULTS.put("config-reloaded", "&aConfiguration reloaded in &e%ms%ms&a.");
    }

    private final Map<String, String> cache = new HashMap<>();
    private String prefix = "";

    /** Re-reads every message from the given configuration. */
    public void reload(FileConfiguration config) {
        cache.clear();
        ConfigurationSection section = config.getConfigurationSection("messages");
        for (Map.Entry<String, String> entry : DEFAULTS.entrySet()) {
            String value = section == null ? null : section.getString(entry.getKey());
            cache.put(entry.getKey(), value != null ? value : entry.getValue());
        }
        prefix = Text.colorize(cache.getOrDefault("prefix", ""));
    }

    public String prefix() {
        return prefix;
    }

    /**
     * Returns a coloured message with {@code %placeholder%} values replaced.
     *
     * @param placeholders alternating key/value pairs, e.g.
     *                     {@code get("map-set", "map", name)}
     */
    public String get(String key, Object... placeholders) {
        String raw = cache.get(key);
        if (raw == null) {
            raw = DEFAULTS.getOrDefault(key, "");
        }
        if (raw.isEmpty()) {
            return "";
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            raw = Text.replace(raw, String.valueOf(placeholders[i]), placeholders[i + 1]);
        }
        return Text.colorize(raw);
    }

    /** Same as {@link #get} but with the configured prefix in front. */
    public String prefixed(String key, Object... placeholders) {
        String message = get(key, placeholders);
        return message.isEmpty() ? "" : prefix + message;
    }

    /** Sends a prefixed message, skipping messages the admin blanked out. */
    public void send(CommandSender receiver, String key, Object... placeholders) {
        if (receiver == null) {
            return;
        }
        String message = prefixed(key, placeholders);
        if (!message.isEmpty()) {
            receiver.sendMessage(message);
        }
    }

    /** Sends a message without the prefix. */
    public void sendRaw(CommandSender receiver, String key, Object... placeholders) {
        if (receiver == null) {
            return;
        }
        String message = get(key, placeholders);
        if (!message.isEmpty()) {
            receiver.sendMessage(message);
        }
    }

    /** Broadcasts a prefixed message to every online player and the console. */
    public void broadcast(String key, Object... placeholders) {
        String message = prefixed(key, placeholders);
        if (message.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
