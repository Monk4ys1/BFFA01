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
}
