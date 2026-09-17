package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.game.Arena;
import infinode.org.example.bFFA01.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Admin command.
 *
 * <p>5.x adds arena deletion, arena listing, per-arena Y levels, a live reload
 * and tab completion for all of it.
 */
public final class BFFACommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "help", "setmap", "delmap", "maps", "setlevel", "swapmap",
            "reload", "addcoins", "removecoins", "setcoins", "resetstats");

    private final BFFA01 plugin;

    public BFFACommand(BFFA01 plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bffa.admin")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setmap" -> setMap(sender, args);
            case "delmap" -> deleteMap(sender, args);
            case "maps" -> listMaps(sender);
            case "setlevel" -> setLevel(sender, args);
            case "swapmap" -> swapMap(sender, args);
            case "reload" -> reload(sender);
            case "addcoins" -> changeCoins(sender, args, CoinAction.ADD);
            case "removecoins" -> changeCoins(sender, args, CoinAction.REMOVE);
            case "setcoins" -> changeCoins(sender, args, CoinAction.SET);
            case "resetstats" -> resetStats(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Subcommands
    // ------------------------------------------------------------------

    private void setMap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length != 2) {
            usage(sender, "/bffa setmap <name>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        plugin.mapManager().saveArena(Arena.fromLocation(id, player.getLocation(), plugin.settings()));
        plugin.messages().send(sender, "map-set", "map", id);
    }

    private void deleteMap(CommandSender sender, String[] args) {
        if (args.length != 2) {
            usage(sender, "/bffa delmap <name>");
            return;
        }
        if (plugin.mapManager().deleteArena(args[1])) {
            plugin.messages().send(sender, "map-deleted", "map", args[1]);
        } else {
            plugin.messages().send(sender, "map-unknown", "map", args[1]);
        }
    }

    private void listMaps(CommandSender sender) {
        List<Arena> arenas = plugin.mapManager().arenas();
        if (arenas.isEmpty()) {
            plugin.messages().send(sender, "map-none");
            return;
        }
        String names = arenas.stream()
                .map(arena -> (arena.equals(plugin.mapManager().current()) ? "&a" : "&7") + arena.id()
                        + (arena.isWorldLoaded() ? "" : " &c(world missing)"))
                .collect(Collectors.joining("&8, "));
        plugin.messages().send(sender, "map-list", "count", arenas.size(), "maps", Text.colorize(names));
    }

    private void setLevel(CommandSender sender, String[] args) {
        if (args.length != 4) {
            usage(sender, "/bffa setlevel <map> <death|safe> <y>");
            return;
        }
        Arena arena = plugin.mapManager().arena(args[1]).orElse(null);
        if (arena == null) {
            plugin.messages().send(sender, "map-unknown", "map", args[1]);
            return;
        }
        Integer level = parseInt(sender, args[3]);
        if (level == null) {
            return;
        }
        boolean death = args[2].equalsIgnoreCase("death");
        if (!death && !args[2].equalsIgnoreCase("safe")) {
            usage(sender, "/bffa setlevel <map> <death|safe> <y>");
            return;
        }
        Arena updated = death
                ? arena.withLevels(level, arena.safezoneY())
                : arena.withLevels(arena.deathY(), level);
        plugin.mapManager().saveArena(updated);
        sender.sendMessage(plugin.messages().prefix() + Text.colorize("&aSet the "
                + (death ? "death" : "safe zone") + " level of &e" + arena.id() + " &ato &b" + level + "&a."));
    }

    private void swapMap(CommandSender sender, String[] args) {
        if (args.length > 2) {
            usage(sender, "/bffa swapmap [map]");
            return;
        }
        String target = args.length == 2 ? args[1] : null;
        if (target != null && plugin.mapManager().arena(target).isEmpty()) {
            plugin.messages().send(sender, "map-unknown", "map", target);
            return;
        }
        plugin.mapManager().forceSwap(target);
        sender.sendMessage(plugin.messages().prefix() + Text.colorize("&aStarting the swap countdown."));
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reloadEverything();
        plugin.messages().send(sender, "config-reloaded", "ms", System.currentTimeMillis() - start);
    }

    private enum CoinAction {
        ADD,
        REMOVE,
        SET
    }

    private void changeCoins(CommandSender sender, String[] args, CoinAction action) {
        String name = action.name().toLowerCase(Locale.ROOT) + "coins";
        if (args.length != 3) {
            usage(sender, "/bffa " + name + " <player> <amount>");
            return;
        }
        PlayerData data = resolve(sender, args[1]);
        if (data == null) {
            return;
        }
        Integer amount = parseInt(sender, args[2]);
        if (amount == null) {
            return;
        }

        switch (action) {
            case ADD -> data.addCoins(amount);
            case REMOVE -> data.addCoins(-amount);
            case SET -> data.coins(amount);
        }
        plugin.dataManager().markDirty();

        String messageKey = switch (action) {
            case ADD -> "coins-given";
            case REMOVE -> "coins-taken";
            case SET -> "coins-set";
        };
        plugin.messages().send(sender, messageKey, "amount", amount, "player", data.name());

        Player online = Bukkit.getPlayer(data.uuid());
        if (online != null && action == CoinAction.ADD) {
            plugin.messages().send(online, "coins-received", "amount", amount);
        }
    }

    private void resetStats(CommandSender sender, String[] args) {
        if (args.length != 2) {
            usage(sender, "/bffa resetstats <player>");
            return;
        }
        PlayerData data = resolve(sender, args[1]);
        if (data == null) {
            return;
        }
        data.kills(0);
        data.deaths(0);
        data.bestStreak(0);
        plugin.dataManager().markDirty();
        plugin.messages().send(sender, "stats-reset", "player", data.name());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Resolves an online player first, then the offline cache. */
    private PlayerData resolve(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerData data = online != null ? plugin.dataManager().get(online) : plugin.dataManager().findByName(name);
        if (data == null) {
            plugin.messages().send(sender, "player-not-found", "player", name);
        }
        return data;
    }

    private Integer parseInt(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "invalid-number", "input", raw);
            return null;
        }
    }

    private void usage(CommandSender sender, String usage) {
        sender.sendMessage(plugin.messages().prefix() + Text.colorize("&cUsage: &7" + usage));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.colorize("&8&m                                        "));
        sender.sendMessage(Text.center("&b&lBuildFFA &7Admin"));
        sender.sendMessage("");
        help(sender, "/bffa setmap <name>", "Save your position as an arena spawn");
        help(sender, "/bffa delmap <name>", "Delete an arena");
        help(sender, "/bffa maps", "List every arena");
        help(sender, "/bffa setlevel <map> <death|safe> <y>", "Set the kill and safe zone height");
        help(sender, "/bffa swapmap [map]", "Start the swap countdown now");
        help(sender, "/bffa addcoins <player> <amount>", "Give coins");
        help(sender, "/bffa removecoins <player> <amount>", "Take coins");
        help(sender, "/bffa setcoins <player> <amount>", "Set a balance");
        help(sender, "/bffa resetstats <player>", "Clear kills, deaths and best streak");
        help(sender, "/bffa reload", "Reload every configuration file");
        help(sender, "/build [player]", "Toggle build mode");
        sender.sendMessage(Text.colorize("&8&m                                        "));
    }

    private void help(CommandSender sender, String command, String description) {
        sender.sendMessage(Text.colorize(" &b" + command + " &8- &7" + description));
    }

    // ------------------------------------------------------------------
    // Tab completion
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bffa.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "delmap", "setlevel", "swapmap" -> filter(arenaIds(), args[1]);
                case "addcoins", "removecoins", "setcoins", "resetstats" -> filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("setlevel")) {
            return filter(List.of("death", "safe"), args[2]);
        }
        return List.of();
    }

    private List<String> arenaIds() {
        List<String> ids = new ArrayList<>();
        for (Arena arena : plugin.mapManager().arenas()) {
            ids.add(arena.id());
        }
        return ids;
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
