package infinode.org.example.bFFA01.hooks.vault;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shop prices and kill rewards. Vault is used only for the modes selected in config.
 * Missing Vault keeps the internal coin balance.
 */
public final class CurrencyRouter {

    private final BFFA01 plugin;
    private volatile VaultBridge vault = VaultBridge.UNAVAILABLE;
    private volatile boolean warned;
    private final ConcurrentHashMap<UUID, Hold> vaultBalance = new ConcurrentHashMap<>();

    public CurrencyRouter(BFFA01 plugin) {
        this.plugin = plugin;
    }

    public void vault(VaultBridge bridge) {
        vault = bridge == null ? VaultBridge.UNAVAILABLE : bridge;
    }

    public VaultBridge vault() {
        return vault;
    }

    public boolean canAfford(Player player, int cost) {
        if (player == null || cost < 0) {
            return false;
        }
        if (cost == 0) {
            return true;
        }
        if (vaultShop()) {
            return vault.available() && vault.has(player.getUniqueId(), cost);
        }
        return plugin.getDataManager().getCoins(player.getUniqueId()) >= cost;
    }

    public boolean charge(Player player, int cost) {
        if (player == null || cost < 0) {
            return false;
        }
        if (cost == 0) {
            return true;
        }
        boolean paid;
        if (vaultShop()) {
            paid = vault.available() && vault.withdraw(player.getUniqueId(), cost);
        } else {
            paid = plugin.getDataManager().removeCoins(player.getUniqueId(), cost);
        }
        if (paid) {
            vaultBalance.remove(player.getUniqueId());
        }
        return paid;
    }

    public boolean refund(Player player, int cost) {
        if (player == null || cost <= 0) {
            return false;
        }
        boolean returned;
        if (vaultShop()) {
            returned = vault.available() && vault.deposit(player.getUniqueId(), cost);
        } else {
            returned = plugin.getDataManager().addCoins(player.getUniqueId(), cost);
        }
        if (returned) {
            vaultBalance.remove(player.getUniqueId());
        }
        return returned;
    }

    public String balance(Player player) {
        if (player == null) {
            return "0";
        }
        if (usesVault()) {
            long now = System.currentTimeMillis();
            Hold held = vaultBalance.get(player.getUniqueId());
            if (held != null && now - held.at < 1_000L) {
                return held.text;
            }
            String formatted = vault.formatted(player.getUniqueId());
            if (formatted == null || formatted.isBlank()) {
                formatted = "0";
            } else if (formatted.length() > 32) {
                formatted = formatted.substring(0, 32);
            }
            if (vaultBalance.size() > 256) {
                vaultBalance.clear();
            }
            vaultBalance.put(player.getUniqueId(), new Hold(now, formatted));
            return formatted;
        }
        if (plugin.getDataManager() == null) {
            return "0";
        }
        return Integer.toString(Math.max(0, plugin.getDataManager().getCoins(player.getUniqueId())));
    }

    public boolean usesVault() {
        return vaultShop();
    }

    public void rewardKill(Player player) {
        if (player == null) {
            return;
        }
        int amount = IntegrationSettings.killAmount(IntegrationSettings.config(plugin));
        if (amount <= 0) {
            return;
        }
        String mode = IntegrationSettings.killMode(IntegrationSettings.config(plugin));
        if ("internal".equals(mode) || "both".equals(mode)) {
            plugin.getDataManager().addCoins(player.getUniqueId(), amount);
        }
        if (("vault".equals(mode) || "both".equals(mode)) && vault.available()) {
            vault.deposit(player.getUniqueId(), amount);
        }
    }

    public static CurrencyRouter get(BFFA01 plugin) {
        try {
            if (plugin != null && plugin.services() != null) {
                return plugin.services().get(CurrencyRouter.class).orElseGet(() -> new CurrencyRouter(plugin));
            }
        } catch (Throwable ignored) {
            // A test double may not stub services().
        }
        return new CurrencyRouter(plugin);
    }

    private static final class Hold {
        private final long at;
        private final String text;

        private Hold(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    private boolean vaultShop() {
        boolean selected = "vault".equals(IntegrationSettings.shopMode(IntegrationSettings.config(plugin)));
        if (selected && !vault.available() && !warned) {
            warned = true;
            try {
                plugin.getLogger().warning("Shop currency is Vault, but no economy provider is connected.");
            } catch (Throwable ignored) {
                warned = true;
            }
        }
        return selected;
    }
}
