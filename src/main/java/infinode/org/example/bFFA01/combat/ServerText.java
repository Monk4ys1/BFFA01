package infinode.org.example.bFFA01.combat;

import java.lang.reflect.Method;

/**
 * Server MiniMessage, not the relocated Adventure copy inside this jar.
 * TextDisplay.text expects the server's component type.
 */
final class ServerText {

    private static volatile Method miniMessage;
    private static volatile Method deserialize;
    private static volatile boolean unavailable;

    private ServerText() {
    }

    static Object component(String mini) {
        if (mini == null || mini.isBlank() || unavailable) {
            return null;
        }
        try {
            if (miniMessage == null || deserialize == null) {
                Class<?> type = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
                miniMessage = type.getMethod("miniMessage");
                deserialize = type.getMethod("deserialize", String.class);
            }
            Object parser = miniMessage.invoke(null);
            return deserialize.invoke(parser, mini);
        } catch (Throwable ex) {
            unavailable = true;
            return null;
        }
    }
}
