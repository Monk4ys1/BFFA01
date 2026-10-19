package infinode.org.example.bFFA01.ui;

import infinode.org.example.bFFA01.text.MiniText;
import infinode.org.example.bFFA01.text.ThemeText;
import infinode.org.example.bFFA01.ui.chat.ChatStyle;
import infinode.org.example.bFFA01.ui.menu.KitLayout;
import infinode.org.example.bFFA01.ui.menu.MenuPages;
import infinode.org.example.bFFA01.ui.scoreboard.SidebarFit;
import infinode.org.example.bFFA01.ui.vote.MapVotes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualLogicTest {

    @Test
    void paletteTokensCannotInjectTags() {
        String applied = ThemeText.apply("<%color_primary%>Hi %icon_map%", ThemeText.defaultColors(), ThemeText.defaultIcons());
        assertTrue(applied.contains("<#7ad7ff>"));
        assertTrue(applied.contains("Hi"));
        assertFalse(applied.contains("%icon_map%"));
        assertEquals("#ffffff", ThemeText.hex("<red>", "#ffffff"));
        assertEquals("*", ThemeText.icon("<red>", "*"));
    }

    @Test
    void longGradientKeepsTheWordsInsideTheTeamCaps() {
        String rendered = MiniText.render("<gradient:#7ad7ff:#6e8bff>BuildFFA Map Rotation</gradient>", Map.of());
        assertTrue(rendered.length() > 128, rendered);
        SidebarFit.Pieces pieces = SidebarFit.fit(rendered, 3);
        assertTrue(pieces.prefix().length() <= SidebarFit.PREFIX_CAP);
        assertTrue(pieces.suffix().length() <= SidebarFit.SUFFIX_CAP);
        assertFalse(pieces.prefix().endsWith("§"));
        assertFalse(pieces.suffix().endsWith("§"));
        assertEquals("BuildFFA Map Rotation", visible(pieces.prefix()) + visible(pieces.suffix()));
    }

    @Test
    void sidebarEntriesStayUnique() {
        assertEquals(20, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).stream()
                .map(SidebarFit::entry)
                .distinct()
                .count());
    }

    @Test
    void plainLineSplitsOnACharacterBoundary() {
        String text = "a".repeat(70);
        SidebarFit.Pieces pieces = SidebarFit.fit(text, 0);
        assertEquals(64, pieces.prefix().length());
        assertEquals(6, pieces.suffix().length());
        assertEquals(text, pieces.prefix() + pieces.suffix());
    }

    @Test
    void chatDropsFormattingAndFormatStringStaysSafe() {
        assertEquals("hello  there", ChatStyle.message("hello <red>&a%there"));
        assertFalse(ChatStyle.message("hello <red>&a%there").contains("<"));
        String format = ChatStyle.format("Name » ");
        assertEquals("Name » §7hello", String.format(format, "ignored", "hello"));
        assertTrue(ChatStyle.message("a".repeat(400)).length() <= ChatStyle.LIMIT);
    }

    @Test
    void kitOrderPrefersTheSavedSlots() {
        List<String> resolved = KitLayout.resolve(
                List.of("IRON_SWORD", "STICK", "SANDSTONE"),
                Map.of(2, "IRON_SWORD", 0, "missing"));
        assertEquals("STICK", resolved.get(0));
        assertEquals("SANDSTONE", resolved.get(1));
        assertEquals("IRON_SWORD", resolved.get(2));
        assertEquals("IRON_SWORD", KitLayout.swap(resolved, 2, 0).get(0));
    }

    @Test
    void pagesClampAndSlice() {
        assertEquals(2, MenuPages.count(8, 5));
        assertEquals(1, MenuPages.count(0, 5));
        assertEquals(List.of("c"), MenuPages.slice(List.of("a", "b", "c"), 1, 2));
        assertEquals(0, MenuPages.clamp(-3, 2));
        assertEquals(1, MenuPages.clamp(9, 2));
    }

    @Test
    void votesPickTheLeaderAndBreakTiesWithTheSeed() {
        MapVotes votes = new MapVotes();
        UUID one = UUID.randomUUID();
        UUID two = UUID.randomUUID();
        UUID three = UUID.randomUUID();
        assertFalse(votes.vote(one, "nope", Set.of("alpha", "beta")));
        assertTrue(votes.vote(one, "alpha", Set.of("alpha", "beta")));
        assertTrue(votes.vote(two, "beta", Set.of("alpha", "beta")));
        assertTrue(votes.vote(three, "alpha", Set.of("alpha", "beta")));
        assertEquals("alpha", votes.winner(List.of("beta", "alpha"), 0));
        votes.vote(two, "alpha", Set.of("alpha", "beta"));
        assertNull(new MapVotes().winner(List.of("alpha"), 1));
        MapVotes tied = new MapVotes();
        tied.vote(one, "alpha", Set.of("alpha", "beta"));
        tied.vote(two, "beta", Set.of("alpha", "beta"));
        assertEquals("alpha", tied.winner(List.of("alpha", "beta"), 0));
        assertEquals("beta", tied.winner(List.of("alpha", "beta"), 1));
        assertEquals("4:05", ClockText.mmss(245));
        assertEquals("0:00", ClockText.mmss(-4));
    }

    private static String visible(String line) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '§' && i + 1 < line.length()) {
                if ((line.charAt(i + 1) == 'x' || line.charAt(i + 1) == 'X') && i + 13 < line.length()) {
                    i += 13;
                } else {
                    i++;
                }
                continue;
            }
            out.append(line.charAt(i));
        }
        return out.toString();
    }
}
