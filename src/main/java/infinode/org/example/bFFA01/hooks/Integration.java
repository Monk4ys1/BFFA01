package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;

/**
 * A soft dependency. {@link #enable} runs only while {@link #pluginName()} is loaded.
 */
public interface Integration {

    String id();

    String pluginName();

    void enable(BFFA01 plugin);

    void disable();

    /**
     * Config can turn a present plugin off without unloading it.
     */
    default boolean available(BFFA01 plugin) {
        return true;
    }

    default void reload(BFFA01 plugin) {
    }
}
