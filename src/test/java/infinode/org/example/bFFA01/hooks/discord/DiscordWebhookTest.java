package infinode.org.example.bFFA01.hooks.discord;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DiscordWebhookTest {

    @Test
    void templatesReplaceNamedTokensAndDropControlCharacters() {
        String filled = DiscordWebhook.fill("%killer% killed %victim%", Map.of(
                "killer", DiscordWebhook.clean("A`\nB"),
                "victim", DiscordWebhook.clean("C")
        ));
        assertEquals("AB killed C", filled);
        assertFalse(filled.contains("`"));
    }

    @Test
    void descriptionsAreCapped() {
        String filled = DiscordWebhook.fill("x".repeat(2000), Map.of());
        assertEquals(1800, filled.length());
    }
}
