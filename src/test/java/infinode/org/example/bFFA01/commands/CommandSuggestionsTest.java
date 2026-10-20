package infinode.org.example.bFFA01.commands;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandSuggestionsTest {

    @Test
    void prefixIsCaseInsensitive() {
        List<String> matches = CommandSuggestions.filter("S", List.of("setmap", "swapmap", "reload"));
        assertEquals(List.of("setmap", "swapmap"), matches);
    }
}
