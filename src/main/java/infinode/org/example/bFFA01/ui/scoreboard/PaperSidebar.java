package infinode.org.example.bFFA01.ui.scoreboard;

import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;

/**
 * Paper can show a full gradient on a sidebar line and hide the red score number.
 * Those methods are absent on Spigot, so they are only reached through reflection.
 */
public final class PaperSidebar {

    private static volatile Method miniMessage;
    private static volatile Method deserialize;
    private static volatile Method blank;
    private static volatile boolean componentUnavailable;
    private static volatile boolean numberUnavailable;

    private PaperSidebar() {
    }

    public static boolean tryShow(Team team, Score score, String miniMessageText) {
        if (team == null || score == null || componentUnavailable) {
            return false;
        }
        Object component = component(miniMessageText);
        if (component == null) {
            return false;
        }
        try {
            Method name = componentMethod(score, "customName");
            if (name == null) {
                componentUnavailable = true;
                return false;
            }
            name.invoke(score, component);
            team.setPrefix("");
            team.setSuffix("");
            tryBlank(score);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    public static boolean tryTitle(Objective objective, String miniMessageText) {
        if (objective == null || componentUnavailable) {
            return false;
        }
        Object component = component(miniMessageText);
        if (component == null) {
            return false;
        }
        try {
            Method name = componentMethod(objective, "displayName");
            if (name == null) {
                return false;
            }
            name.invoke(objective, component);
            return true;
        } catch (Throwable ex) {
            return false;
        }
    }

    public static boolean tryRawPrefix(Team team, String legacy) {
        if (team == null || legacy == null) {
            return false;
        }
        try {
            team.setPrefix(legacy);
            team.setSuffix("");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static void tryBlank(Score score) {
        if (score == null || numberUnavailable) {
            return;
        }
        try {
            Object format = blank();
            Method method = componentMethod(score, "numberFormat");
            if (format == null || method == null) {
                numberUnavailable = true;
                return;
            }
            method.invoke(score, format);
        } catch (Throwable ex) {
            numberUnavailable = true;
        }
    }

    public static void tryBlankObjective(Objective objective) {
        if (objective == null || numberUnavailable) {
            return;
        }
        try {
            Object format = blank();
            Method method = componentMethod(objective, "numberFormat");
            if (format == null || method == null) {
                numberUnavailable = true;
                return;
            }
            method.invoke(objective, format);
        } catch (Throwable ex) {
            numberUnavailable = true;
        }
    }

    private static Object component(String miniMessageText) {
        if (miniMessageText == null || componentUnavailable) {
            return null;
        }
        try {
            if (miniMessage == null || deserialize == null) {
                Class<?> type = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
                miniMessage = type.getMethod("miniMessage");
                deserialize = type.getMethod("deserialize", String.class);
            }
            Object parser = miniMessage.invoke(null);
            return deserialize.invoke(parser, miniMessageText);
        } catch (ClassNotFoundException ex) {
            componentUnavailable = true;
            return null;
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object blank() throws ReflectiveOperationException {
        if (blank != null) {
            return blank.invoke(null);
        }
        Class<?> type = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
        blank = type.getMethod("blank");
        return blank.invoke(null);
    }

    private static Method componentMethod(Object target, String name) {
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == 1) {
                Class<?> parameter = candidate.getParameterTypes()[0];
                if (parameter != String.class && !parameter.isPrimitive()) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
