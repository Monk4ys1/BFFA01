package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.data.PlayerData;
import infinode.org.example.bFFA01.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Statistics menu: the viewed player's numbers plus the kill leaderboard.
 * Reachable from {@code /stats} and from the shop.
 */
public final class StatsGui extends Gui {

    private static final int PROFILE_SLOT = 13;
    private static final int FIRST_LEADERBOARD_SLOT = 28;
    private static final int LEADERBOARD_LENGTH = 5;
    private static final int SHOP_SLOT = 39;
    private static final int CLOSE_SLOT = 41;

    private final PlayerData target;

    public StatsGui(BFFA01 plugin, Player viewer, PlayerData target) {
        super(plugin, viewer, 5, "&8» &e&lStatistics");
        this.target = target;
    }

    @Override
    public void render() {
        getInventory().clear();
        drawFrame(FRAME_MATERIAL);

        set(PROFILE_SLOT, profileIcon());
        set(22, ItemBuilder.of(Material.NAME_TAG)
                .name("&6&lLeaderboard")
                .lore("&7Top " + LEADERBOARD_LENGTH + " players by kills.")
                .build());

        List<PlayerData> top = plugin.dataManager().topByKills(LEADERBOARD_LENGTH);
        for (int index = 0; index < LEADERBOARD_LENGTH; index++) {
            int slot = FIRST_LEADERBOARD_SLOT + index;
            if (index < top.size()) {
                set(slot, leaderboardIcon(index + 1, top.get(index)));
            } else {
                set(slot, ItemBuilder.of(Material.GRAY_DYE)
                        .name("&8#" + (index + 1))
                        .lore("&8Nobody yet.")
                        .build());
            }
        }

        set(SHOP_SLOT, ItemBuilder.of(Material.EMERALD)
                .name("&a&lShop")
                .lore("&7Spend your coins.", "", "&8» &aClick to open")
                .build());
        set(CLOSE_SLOT, closeButton());

        fillEmpty(SEPARATOR_MATERIAL);
    }

    private ItemStack profileIcon() {
        int rank = plugin.dataManager().rankOf(target.uuid());
        Player online = Bukkit.getPlayer(target.uuid());
        int streak = online == null ? 0 : plugin.killstreakManager().streak(online);

        List<String> lore = new ArrayList<>();
        lore.add("&8&m                         ");
        lore.add("&7Kills: &a" + target.kills());
        lore.add("&7Deaths: &c" + target.deaths());
        lore.add("&7K/D: &6" + String.format("%.2f", target.kd()));
        lore.add("");
        lore.add("&7Current streak: &d" + streak);
        lore.add("&7Best streak: &d" + target.bestStreak());
        lore.add("&7Coins: &6" + target.coins());
        lore.add("&7Rank: &b" + (rank > 0 ? "#" + rank : "unranked"));
        lore.add("&7Upgrades: &b" + target.upgrades().size());
        lore.add("&8&m                         ");

        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOf(Bukkit.getOfflinePlayer(target.uuid()))
                .name("&b&l" + target.name())
                .lore(lore)
                .build();
    }

    private ItemStack leaderboardIcon(int rank, PlayerData data) {
        String colour = switch (rank) {
            case 1 -> "&6";
            case 2 -> "&f";
            case 3 -> "&c";
            default -> "&7";
        };
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOf(Bukkit.getOfflinePlayer(data.uuid()))
                .name(colour + "&l#" + rank + " &r" + colour + data.name())
                .lore("&7Kills: &a" + data.kills(),
                        "&7Deaths: &c" + data.deaths(),
                        "&7K/D: &6" + String.format("%.2f", data.kd()))
                .glow(rank == 1)
                .build();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(getInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        } else if (slot == SHOP_SLOT) {
            new ShopGui(plugin, viewer).open();
        }
    }
}
