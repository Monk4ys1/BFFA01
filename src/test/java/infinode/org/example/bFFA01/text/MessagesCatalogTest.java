package infinode.org.example.bFFA01.text;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesCatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void englishAndGermanExposeTheSameKeys() {
        YamlConfiguration english = yaml("lang/en.yml");
        YamlConfiguration german = yaml("lang/de.yml");
        assertEquals(english.getKeys(false), german.getKeys(false));
    }

    @Test
    void selectedLanguageOverridesEnglish() {
        Messages messages = Messages.load(tempDir, "de", null);
        assertTrue(messages.raw("no-permission").contains("keine Rechte"));
        assertTrue(messages.render("player-not-found", Map.of()).contains("Spieler"));
    }

    @Test
    void unknownLanguageStaysOnEnglish() {
        Messages messages = Messages.load(tempDir, "../de", null);
        assertTrue(messages.raw("player-not-found").contains("Player not found"));
    }

    private static YamlConfiguration yaml(String resource) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                MessagesCatalogTest.class.getClassLoader().getResourceAsStream(resource),
                StandardCharsets.UTF_8));
    }
}
