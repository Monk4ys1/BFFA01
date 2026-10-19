package infinode.org.example.bFFA01.hooks.worldguard;

/**
 * Decides whether a build or hit is allowed once WorldGuard has been queried.
 * A missing flag fails open. A missing region does not block the arena.
 */
public final class ArenaDecision {

    private ArenaDecision() {
    }

    public static boolean allows(boolean integrationActive, boolean restriction, boolean regionConfigured,
                                 boolean insideRegion, Boolean flagAllows) {
        if (!integrationActive) {
            return true;
        }
        if (flagAllows != null && !flagAllows) {
            return false;
        }
        return !restriction || !regionConfigured || insideRegion;
    }
}
