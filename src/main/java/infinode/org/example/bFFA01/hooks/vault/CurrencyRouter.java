package infinode.org.example.bFFA01.hooks.vault;

import infinode.org.example.bFFA01.BFFA01;
import infinode.org.example.bFFA01.hooks.IntegrationSettings;
import org.bukkit.entity.Player;

/**
 * Shop prices and kill rewards. Vault is used only for the modes selected in config.
 * Missing Vault keeps the internal coin balance.
 */
public final class CurrencyRouter {

    private final BFFA01 plugin;
    private volatile VaultBridge vault = VaultBridge.UNAVAILABLE;
    private volatile boolean warned;

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
        if (player == null || cost <= 0) {
            return true;
        }
        if (vaultShop()) {
            return vault.available() && vault.has(player.getUniqueId(), cost);
        }
        return plugin.getDataManager().getCoins(player.getUniqueId()) >= cost;
    }

    public boolean charge(Player player, int cost) {
        if (player == null || cost <= 0) {
            return true;
        }
        if (vaultShop()) {
            return vault.available() && vault.withdraw(player.getUniqueId(), cost);
        }
        return plugin.getDataManager().removeCoins(player.getUniqueId(), cost);
    }

    public String balance(Player player) {
        if (player == null) {
            return "0";
        }
        if (usesVault()) {
            String formatted = vault.formatted(player.getUniqueId());
            if (formatted == null || formatted.isBlank()) {
                return "0";
            }
            return formatted.length() > 32 ? formatted.substring(0, 32) : formatted;
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
