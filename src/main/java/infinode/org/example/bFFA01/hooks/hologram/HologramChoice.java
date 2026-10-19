package infinode.org.example.bFFA01.hooks.hologram;

import java.util.Locale;

/**
 * Picks a hologram backend. {@code auto} prefers DecentHolograms, then FancyHolograms,
 * then the built-in armor stands.
 */
public final class HologramChoice {

    private HologramChoice() {
    }

    public static String choose(String configured, boolean decent, boolean fancy) {
        String mode = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "armorstand", "builtin", "internal" -> "armorstand";
            case "decentholograms", "decent" -> decent ? "decentholograms" : "armorstand";
            case "fancyholograms", "fancy" -> fancy ? "fancyholograms" : "armorstand";
            default -> {
                if (decent) {
                    yield "decentholograms";
                }
                if (fancy) {
                    yield "fancyholograms";
                }
                yield "armorstand";
            }
        };
    }
}
