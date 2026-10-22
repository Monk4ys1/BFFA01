package infinode.org.example.bFFA01.shop;

/**
 * Permanent upgrades take a second click. Consumables and owned upgrades do not.
 */
public final class ShopConfirm {

    private ShopConfirm() {
    }

    public static boolean arm(boolean permanent, boolean enabled, boolean owned, String pendingOffer, String offerId) {
        if (!permanent || !enabled || owned || offerId == null || offerId.isBlank()) {
            return false;
        }
        return pendingOffer == null || !pendingOffer.equals(offerId);
    }
}
