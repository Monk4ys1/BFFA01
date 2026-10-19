package infinode.org.example.bFFA01.ui.scoreboard;

import infinode.org.example.bFFA01.text.MiniText;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Team;

import java.util.Map;

/**
 * Writes one sidebar line. Paper gets the original MiniMessage; Spigot gets a fitted legacy line.
 */
public final class SidebarWriter {

    private SidebarWriter() {
    }

    public static void title(Objective objective, String miniMessage) {
        if (objective == null) {
            return;
        }
        String source = miniMessage == null ? "" : miniMessage;
        String legacy = SidebarFit.compress(MiniText.render(source, Map.of()), 128);
        objective.setDisplayName(legacy);
        PaperSidebar.tryTitle(objective, source);
        PaperSidebar.tryBlankObjective(objective);
    }

    public static void line(Team team, Score score, String miniMessage, int index) {
        if (team == null || score == null) {
            return;
        }
        String source = miniMessage == null ? "" : miniMessage;
        if (PaperSidebar.tryShow(team, score, source)) {
            return;
        }
        String legacy = MiniText.render(source, Map.of());
        if (legacy.length() <= SidebarFit.PREFIX_CAP) {
            team.setPrefix(legacy);
            team.setSuffix("");
            PaperSidebar.tryBlank(score);
            return;
        }
        if (PaperSidebar.tryRawPrefix(team, legacy)) {
            PaperSidebar.tryBlank(score);
            return;
        }
        SidebarFit.Pieces pieces = SidebarFit.fit(legacy, index);
        team.setPrefix(pieces.prefix());
        team.setSuffix(pieces.suffix());
        PaperSidebar.tryBlank(score);
    }
}
