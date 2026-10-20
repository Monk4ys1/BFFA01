package infinode.org.example.bFFA01.hooks.vault;

import java.util.UUID;

/**
 * Vault economy, reached only while the Vault plugin is loaded.
 */
public interface VaultBridge {

    VaultBridge UNAVAILABLE = new VaultBridge() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean has(UUID player, int amount) {
            return false;
        }

        @Override
        public boolean withdraw(UUID player, int amount) {
            return false;
        }

        @Override
        public boolean deposit(UUID player, int amount) {
            return false;
        }

        @Override
        public String formatted(UUID player) {
            return "";
        }

        @Override
        public String chatPrefix(UUID player) {
            return "";
        }
    };

    boolean available();

    boolean has(UUID player, int amount);

    boolean withdraw(UUID player, int amount);

    boolean deposit(UUID player, int amount);

    String formatted(UUID player);

    String chatPrefix(UUID player);
}
