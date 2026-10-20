package infinode.org.example.bFFA01.hooks;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationSettingsTest {

    @Test
    void missingKillRewardPaysNothing() {
        assertEquals(0, IntegrationSettings.killAmount(null));
        assertEquals(0, IntegrationSettings.killAmount(new YamlConfiguration()));
        assertEquals("internal", IntegrationSettings.killMode(null));
        assertEquals("internal", IntegrationSettings.shopMode(new YamlConfiguration()));
    }

    @Test
    void vaultModesAndDiscordDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("integrations.vault.shop", "VAULT");
        config.set("integrations.vault.kill-reward", "both");
        config.set("integrations.vault.kill-amount", -4);
        config.set("integrations.worldguard.arena-region", "global");
        config.set("maps.arena.region", "pit");
        config.set("holograms.id", "bad id");
        assertEquals("vault", IntegrationSettings.shopMode(config));
        assertEquals("both", IntegrationSettings.killMode(config));
        assertEquals(0, IntegrationSettings.killAmount(config));
        assertEquals("pit", IntegrationSettings.arenaRegion(config, "arena"));
        assertEquals("global", IntegrationSettings.arenaRegion(config, "other"));
        assertEquals("bffa-top", IntegrationSettings.hologramId(config));

        IntegrationSettings.Discord discord = IntegrationSettings.discord(new YamlConfiguration());
        assertFalse(discord.enabled());
        assertTrue(discord.milestone(10));
        assertEquals(30, discord.rateLimit());
    }
}
