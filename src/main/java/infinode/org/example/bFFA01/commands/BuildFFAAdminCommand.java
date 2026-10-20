package infinode.org.example.bFFA01.commands;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.text.Messages;
import infinode.org.example.bFFA01.text.MiniText;
import infinode.org.example.bFFA01.util.MapNames;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class BuildFFAAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "setmap", "swapmap", "addcoins", "removecoins", "resetstats", "reload", "network"
    );

    private final BFFA01 plugin;
    private final Function<String, Player> onlinePlayers;

    public BuildFFAAdminCommand(BFFA01 plugin) {
        this(plugin, Bukkit::getPlayerExact);
    }

    BuildFFAAdminCommand(BFFA01 plugin, Function<String, Player> onlinePlayers) {
        this.plugin = plugin;
        this.onlinePlayers = onlinePlayers;
    }

    private Player findOnline(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return onlinePlayers.apply(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!allowed(sender, "bffa.admin")) {
                reply(sender, "no-permission", "&cYou do not have permission to do that.", Map.of());
                return true;
            }
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (!allowed(sender, node(subCommand))) {
            reply(sender, "no-permission", "&cYou do not have permission to do that.", Map.of());
            return true;
        }

        switch (subCommand) {
            case "setmap" -> setMap(sender, args);
            case "addcoins" -> changeCoins(sender, args, true);
            case "removecoins" -> changeCoins(sender, args, false);
            case "resetstats" -> resetStats(sender, args);
            case "swapmap" -> swapMap(sender, args);
            case "reload" -> reload(sender);
            case "network" -> network(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args == null || args.length == 0) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> visible = new ArrayList<>();
            for (String sub : SUBS) {
                if (allowed(sender, "bffa.admin") || allowed(sender, node(sub))) {
                    visible.add(sub);
                }
            }
            return CommandSuggestions.filter(args[0], visible);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("setmap".equals(sub) || "swapmap".equals(sub)) {
            return CommandSuggestions.filter(args[args.length - 1], mapNames());
        }
        if (("addcoins".equals(sub) || "removecoins".equals(sub) || "resetstats".equals(sub)) && args.length == 2) {
            return CommandSuggestions.filter(args[1], onlineNames());
        }
        return List.of();
    }

    private void setMap(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            reply(sender, "players-only", "&cOnly players can do that.", Map.of());
            return;
        }
        if (args.length != 2) {
            reply(sender, "command-usage", "&cUsage: /bffa setmap <mapname>", Map.of());
            return;
        }
        String mapName = args[1];
        if (!MapNames.isValid(mapName)) {
            reply(sender, "map-name-invalid", "&cMap name must be 1-32 letters, numbers, underscores, or hyphens.", Map.of());
            return;
        }
        if (plugin.getMapManager() == null || !plugin.getMapManager().defineSpawn(mapName, player.getLocation())) {
            reply(sender, "map-save-failed", "&cCould not save that map spawn.", Map.of());
            return;
        }
        reply(sender, "map-set", "&aSpawn location for map &e%map% &aset successfully.",
                Map.of("map", MapNames.sanitizeLabel(mapName)));
    }

    private void changeCoins(CommandSender sender, String[] args, boolean add) {
        String usage = add ? "&cUsage: /bffa addcoins <player> <amount>" : "&cUsage: /bffa removecoins <player> <amount>";
        if (args.length != 3) {
            reply(sender, "command-usage", usage, Map.of());
            return;
        }
        Player target = findOnline(args[1]);
        if (target == null) {
            reply(sender, "player-not-found", "&cPlayer not found or offline.", Map.of());
            return;
        }
        Integer amount = parsePositiveAmount(sender, args[2]);
        if (amount == null) {
            return;
        }
        boolean ok = add
                ? plugin.getDataManager().addCoins(target.getUniqueId(), amount)
                : plugin.getDataManager().removeCoins(target.getUniqueId(), amount);
        if (!ok) {
            reply(sender, "coins-failed", "&cCould not update that player's coins.", Map.of());
            return;
        }
        Map<String, String> values = Map.of("amount", Integer.toString(amount), "player", target.getName());
        if (add) {
            reply(sender, "coins-added", "&aGave %amount% coins to %player%.", values);
            reply(target, "coins-received", "&aYou received %amount% coins!", values);
        } else {
            reply(sender, "coins-removed", "&aRemoved %amount% coins from %player%.", values);
        }
    }

    private void resetStats(CommandSender sender, String[] args) {
        if (args.length != 2) {
            reply(sender, "command-usage", "&cUsage: /bffa resetstats <player>", Map.of());
            return;
        }
        Player target = findOnline(args[1]);
        if (target == null) {
            reply(sender, "player-not-found", "&cPlayer not found or offline.", Map.of());
            return;
        }
        if (!plugin.getDataManager().resetStats(target.getUniqueId())) {
            reply(sender, "stats-reset-failed", "&cCould not reset that player's stats.", Map.of());
            return;
        }
        reply(sender, "stats-reset", "&aReset stats for %player%.", Map.of("player", target.getName()));
    }

    private void swapMap(CommandSender sender, String[] args) {
        if (plugin.getMapManager() == null) {
            return;
        }
        if (args.length == 1) {
            plugin.getMapManager().forceSwapSequence(null);
            reply(sender, "swapmap-started", "&aForced a map swap. Sequence initiated (3 seconds).", Map.of());
            return;
        }
        if (args.length != 2) {
            reply(sender, "swapmap-usage", "&cUsage: /bffa swapmap [mapname]", Map.of());
            return;
        }
        if (!plugin.getMapManager().hasMap(args[1])) {
            reply(sender, "unknown-map", "&cUnknown map.", Map.of());
            return;
        }
        plugin.getMapManager().forceSwapSequence(args[1]);
        reply(sender, "swapmap-started-named", "&aForced a map swap to '%map%'. Sequence initiated (3 seconds).",
                Map.of("map", MapNames.sanitizeLabel(args[1])));
    }

    private void reload(CommandSender sender) {
        boolean storage = plugin.reloadPluginConfig();
        reply(sender, "reload-done", "&aReloaded config, messages, and the scoreboard.", Map.of());
        if (storage) {
            reply(sender, "reload-storage-ok", "&aStorage reconnected.", Map.of());
        } else {
            reply(sender, "reload-storage-failed", "&cStorage reconnect failed. The previous store is still in use.", Map.of());
        }
    }

    private Integer parsePositiveAmount(CommandSender sender, String raw) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount <= 0) {
                reply(sender, "amount-positive", "&cAmount must be a positive number.", Map.of());
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            reply(sender, "amount-invalid", "&cInvalid amount.", Map.of());
            return null;
        }
    }

    private void sendHelp(CommandSender sender) {
        line(sender, "help-header", "&3--- BuildFFA Admin Commands ---");
        line(sender, "help-setmap", "&b/bffa setmap <name> &7- Set map spawn");
        line(sender, "help-addcoins", "&b/bffa addcoins <player> <amount> &7- Give coins");
        line(sender, "help-removecoins", "&b/bffa removecoins <player> <amount> &7- Remove coins");
        line(sender, "help-resetstats", "&b/bffa resetstats <player> &7- Reset kills/deaths");
        line(sender, "help-swapmap", "&b/bffa swapmap [name] &7- Force swap map (with 3s sequence)");
        line(sender, "help-reload", "&b/bffa reload &7- Reload config and messages");
        line(sender, "help-network", "&b/bffa network &7- Show this server's network status");
    }

    private void network(CommandSender sender) {
        var link = plugin.network();
        if (link == null || !link.enabled()) {
            reply(sender, "network-disabled", "&7This server is not part of a network. Set network.enabled in config.yml.", Map.of());
            return;
        }
        String storage = plugin.getDataManager() == null ? "yaml" : plugin.getDataManager().storageLabel();
        reply(sender, "network-status", "&aServer &e%id% &aon &e%storage%&a. Redis: &e%redis%&a.",
                Map.of(
                        "id", link.serverId(),
                        "storage", storage,
                        "redis", link.redisConnected() ? "connected" : "off"
                ));
    }

    private void line(CommandSender sender, String key, String fallback) {
        sender.sendMessage(render(key, fallback, Map.of()));
    }

    private void reply(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        sender.sendMessage(render("prefix", "&8[&bBuildFFA&8] ", placeholders) + render(key, fallback, placeholders));
    }

    private String render(String key, String fallback, Map<String, String> placeholders) {
        Messages messages = null;
        try {
            messages = plugin.getMessages();
        } catch (Throwable ignored) {
            messages = null;
        }
        if (messages != null) {
            return messages.render(key, placeholders);
        }
        String raw = fallback;
        try {
            if (plugin.getConfig() != null) {
                raw = plugin.getConfig().getString("messages." + key, fallback);
            }
        } catch (Throwable ignored) {
            raw = fallback;
        }
        return MiniText.render(raw == null ? fallback : raw, placeholders);
    }

    private boolean allowed(CommandSender sender, String node) {
        if (sender == null || node == null) {
            return false;
        }
        return sender.hasPermission("bffa.admin") || sender.hasPermission(node);
    }

    private static String node(String sub) {
        return switch (sub) {
            case "setmap" -> "bffa.admin.setmap";
            case "swapmap" -> "bffa.admin.swapmap";
            case "addcoins", "removecoins" -> "bffa.admin.coins";
            case "resetstats" -> "bffa.admin.stats";
            case "reload" -> "bffa.admin.reload";
            default -> "bffa.admin";
        };
    }

    private List<String> mapNames() {
        try {
            if (plugin.getMapManager() == null) {
                return List.of();
            }
            return plugin.getMapManager().mapNames();
        } catch (Throwable ex) {
            return List.of();
        }
    }

    private List<String> onlineNames() {
        try {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        } catch (Throwable ex) {
            return List.of();
        }
    }
}
