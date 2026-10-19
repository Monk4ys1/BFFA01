package infinode.org.example.bFFA01.text;

import infinode.org.example.bFFA01.hooks.PlaceholderResolver;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DisplayedTextTest {

    @Test
    void placeholdersResolveBeforeMiniMessageAndTheme() {
        String prepared = DisplayedText.prepare(
                "<%color_primary%>%kills% <bffa_kills>",
                Map.of("kills", "4"),
                (player, text) -> text.replace("<bffa_kills>", "9"),
                text -> text.replace("%color_primary%", "#7ad7ff"),
                (player, text) -> text,
                null);
        assertEquals("<#7ad7ff>4 9", prepared);
        String legacy = MiniText.render(prepared, Map.of());
        assertFalse(legacy.contains("<bffa"));
        assertTrueVisible(legacy);
    }

    @Test
    void insertedValuesCannotOpenTags() {
        String prepared = DisplayedText.prepare(
                "Hi %player%",
                Map.of("player", "<click:run_command:/op>"),
                PlaceholderResolver.identity(),
                text -> text,
                (player, text) -> text,
                null);
        assertFalse(prepared.contains("<"));
        assertFalse(prepared.contains(">"));
    }

    private static void assertTrueVisible(String legacy) {
        assertFalse(legacy.isBlank());
    }
}
