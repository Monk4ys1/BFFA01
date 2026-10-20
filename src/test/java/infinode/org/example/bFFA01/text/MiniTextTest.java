package infinode.org.example.bFFA01.text;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniTextTest {

    @Test
    void miniMessageBecomesSectionText() {
        String rendered = MiniText.render("<red>Hello", Map.of());
        assertTrue(rendered.contains("Hello"));
        assertTrue(rendered.indexOf('§') >= 0);
    }

    @Test
    void placeholdersCannotOpenTags() {
        String rendered = MiniText.render("Hi %name%", Map.of("name", "<red>&a"));
        assertFalse(rendered.contains("<"));
        assertFalse(rendered.contains("&a"));
        assertTrue(rendered.contains("Hi"));
    }

    @Test
    void legacyAmpersandStillRenders() {
        String rendered = MiniText.render("&cHello", Map.of());
        assertTrue(rendered.contains("Hello"));
        assertTrue(rendered.contains("§"));
    }

    @Test
    void limitKeepsThePrefixInsideTheTeamCap() {
        assertEquals("abc", MiniText.limit("abcdef", 3));
        assertEquals("", MiniText.limit(null, 64));
    }
}
