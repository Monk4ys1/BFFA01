package infinode.org.example.bFFA01.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopConfirmTest {

    @Test
    void permanentUpgradesWaitForASecondClick() {
        assertTrue(ShopConfirm.arm(true, true, false, "", "sharpness"));
        assertTrue(ShopConfirm.arm(true, true, false, "protection", "sharpness"));
        assertFalse(ShopConfirm.arm(true, true, false, "sharpness", "sharpness"));
        assertFalse(ShopConfirm.arm(true, true, true, "", "sharpness"));
        assertFalse(ShopConfirm.arm(false, true, false, "", "pearl"));
        assertFalse(ShopConfirm.arm(true, false, false, "", "sharpness"));
    }
}
