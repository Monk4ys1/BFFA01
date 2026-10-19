package infinode.org.example.bFFA01.hooks.vault;

import infinode.org.example.bFFA01.hooks.Reflects;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Talks to Vault's {@code Economy} and {@code Chat} services without a compile dependency.
 */
public final class VaultEconomy implements VaultBridge {

    private final Object economy;
    private final Object chat;
    private final Logger logger;
    private volatile boolean warned;

    private VaultEconomy(Object economy, Object chat, Logger logger) {
        this.economy = economy;
        this.chat = chat;
        this.logger = logger;
    }

    public static VaultBridge attach(Plugin vault, Logger logger) {
        if (vault == null) {
            return UNAVAILABLE;
        }
        try {
            ClassLoader loader = vault.getClass().getClassLoader();
            Object economy = service(loader, "net.milkbowl.vault.economy.Economy");
            Object chat = null;
            try {
                chat = service(loader, "net.milkbowl.vault.chat.Chat");
            } catch (Throwable ignored) {
                chat = null;
            }
            if (economy == null) {
                if (logger != null) {
                    logger.warning("Vault is installed, but no economy provider is registered. Coins stay internal.");
                }
                return UNAVAILABLE;
            }
            return new VaultEconomy(economy, chat, logger);
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("Vault economy did not attach: " + ex.getMessage());
            }
            return UNAVAILABLE;
        }
    }

    @Override
    public boolean available() {
        return economy != null;
    }

    @Override
    public boolean has(UUID player, int amount) {
        Object response = call(player, "has", amount);
        return response instanceof Boolean ok && ok;
    }

    @Override
    public boolean withdraw(UUID player, int amount) {
        return success(call(player, "withdrawPlayer", amount));
    }

    @Override
    public boolean deposit(UUID player, int amount) {
        return success(call(player, "depositPlayer", amount));
    }

    @Override
    public String formatted(UUID player) {
        Object balance = call(player, "getBalance", -1);
        if (balance instanceof Number number) {
            double value = number.doubleValue();
            Object formatted = Reflects.call(economy, "format", value);
            if (formatted instanceof String text && !text.isBlank()) {
                return text;
            }
            if (value == Math.rint(value)) {
                return Long.toString((long) value);
            }
            return Double.toString(value);
        }
        return "";
    }

    @Override
    public String chatPrefix(UUID player) {
        if (chat == null) {
            return "";
        }
        OfflinePlayer offline = offline(player);
        if (offline == null) {
            return "";
        }
        Object prefix = Reflects.call(chat, "getPlayerPrefix", null, offline);
        if (!(prefix instanceof String text)) {
            org.bukkit.entity.Player online = offline.getPlayer();
            if (online != null) {
                prefix = Reflects.call(chat, "getPlayerPrefix", online);
            }
        }
        return prefix instanceof String text ? text : "";
    }

    private Object call(UUID player, String method, int amount) {
        OfflinePlayer offline = offline(player);
        if (offline == null || economy == null) {
            return null;
        }
        try {
            if (amount < 0) {
                return first(economy, method, new Object[]{offline}, new Object[]{offline.getName()});
            }
            double value = amount;
            return first(economy, method, new Object[]{offline, value}, new Object[]{offline.getName(), value});
        } catch (Throwable ex) {
            warn(ex);
            return null;
        }
    }

    private boolean success(Object response) {
        if (response instanceof Boolean ok) {
            return ok;
        }
        Object flag = Reflects.call(response, "transactionSuccess");
        return flag instanceof Boolean ok && ok;
    }

    private static Object first(Object target, String method, Object[] first, Object[] second) {
        Object value = Reflects.call(target, method, first);
        if (value != null) {
            return value;
        }
        return Reflects.call(target, method, second);
    }

    private OfflinePlayer offline(UUID player) {
        if (player == null) {
            return null;
        }
        try {
            return Bukkit.getOfflinePlayer(player);
        } catch (Throwable ex) {
            warn(ex);
            return null;
        }
    }

    private void warn(Throwable ex) {
        if (warned || logger == null) {
            return;
        }
        warned = true;
        logger.warning("Vault call failed: " + ex.getMessage());
    }

    private static Object service(ClassLoader loader, String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, true, loader);
        Object registration = Bukkit.getServicesManager().getRegistration(type);
        if (registration == null) {
            return null;
        }
        Method provider = registration.getClass().getMethod("getProvider");
        return provider.invoke(registration);
    }
}
