package infinode.org.example.bFFA01.ui.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuViewTest {

    @Test
    void formsAndChestsShareTheSameClickableActions() {
        MenuView view = new MenuView(
                "hub",
                0,
                "",
                5,
                "BuildFFA",
                "BuildFFA",
                List.of("Coins 12"),
                List.of(
                        new MenuView.Control(20, "open:shop", "EMERALD", "Shop", List.of(), false, "Shop"),
                        new MenuView.Control(4, "none", "SUNFLOWER", "Coins", List.of("12"), false, "Coins 12"),
                        new MenuView.Control(22, "page0", "PAPER", "Kills", List.of("4"), false, "Kills 4"),
                        new MenuView.Control(40, "close", "BARRIER", "Close", List.of(), false, "Close")
                ),
                List.of(),
                -1,
                "GRAY_STAINED_GLASS_PANE",
                List.of(),
                List.of());

        assertEquals(List.of("open:shop", "close"), view.clickable().stream().map(MenuView.Control::action).toList());
        assertEquals(List.of("Coins 12"), view.body());
    }
}
