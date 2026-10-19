package infinode.org.example.bFFA01.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandSuggestions {

    private CommandSuggestions() {
    }

    public static List<String> filter(String token, List<String> options) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        if (options == null) {
            return matches;
        }
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
