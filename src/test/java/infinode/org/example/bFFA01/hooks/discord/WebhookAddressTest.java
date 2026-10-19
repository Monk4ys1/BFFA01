package infinode.org.example.bFFA01.hooks.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookAddressTest {

    @Test
    void acceptsDiscordWebhookHosts() {
        assertTrue(WebhookAddress.allowed("https://discord.com/api/webhooks/1/token"));
        assertTrue(WebhookAddress.allowed("https://discordapp.com/api/webhooks/1/token"));
        assertTrue(WebhookAddress.allowed("https://canary.discord.com/api/webhooks/1/token"));
        assertTrue(WebhookAddress.allowed("https://ptb.discord.com/api/webhooks/1/token"));
    }

    @Test
    void rejectsOtherDestinations() {
        assertFalse(WebhookAddress.allowed(null));
        assertFalse(WebhookAddress.allowed(""));
        assertFalse(WebhookAddress.allowed("http://discord.com/api/webhooks/1/token"));
        assertFalse(WebhookAddress.allowed("https://user:pass@discord.com/api/webhooks/1/token"));
        assertFalse(WebhookAddress.allowed("https://discord.com.evil.example/api/webhooks/1/token"));
        assertFalse(WebhookAddress.allowed("https://example.com/api/webhooks/1/token"));
        assertFalse(WebhookAddress.allowed("https://discord.com/api/v10/channels/1"));
        assertFalse(WebhookAddress.allowed("not a url"));
    }
}
