package infinode.org.example.bFFA01.shop;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopOfferTest {

    @Test
    void idsSlotsAndCostsStayUniqueAndStable() {
        Set<String> ids = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (ShopOffer offer : ShopOffer.values()) {
            assertTrue(ids.add(offer.id()), offer.id());
            assertTrue(slots.add(offer.slot()), offer.id());
            assertTrue(offer.cost() > 0);
        }
        assertEquals(100, ShopOffer.SHARPNESS.cost());
        assertEquals(150, ShopOffer.PROTECTION.cost());
        assertEquals(60, ShopOffer.FIREBALL.cost());
        assertEquals(45, ShopOffer.SWITCHER.cost());
        assertEquals(35, ShopOffer.PLATFORM.cost());
        assertTrue(ShopOffer.SHARPNESS.permanent());
        assertTrue(!ShopOffer.FIREBALL.permanent());
    }
}
