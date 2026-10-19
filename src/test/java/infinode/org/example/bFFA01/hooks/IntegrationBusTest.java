package infinode.org.example.bFFA01.hooks;

import infinode.org.example.bFFA01.BFFA01;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationBusTest {

    @Test
    void refreshEnablesAndDisablesWithThePlugin() {
        IntegrationBus bus = new IntegrationBus();
        Fake fake = new Fake();
        bus.register(fake);

        bus.refresh(name -> "FakePlugin".equals(name), Logger.getLogger("bffa-test"), null);
        assertTrue(bus.isActive("fake"));
        assertEquals(1, fake.enabled);

        bus.refresh(name -> false, Logger.getLogger("bffa-test"), null);
        assertFalse(bus.isActive("fake"));
        assertEquals(0, fake.enabled);
    }

    private static final class Fake implements Integration {
        private int enabled;

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public String pluginName() {
            return "FakePlugin";
        }

        @Override
        public void enable(BFFA01 plugin) {
            enabled++;
        }

        @Override
        public void disable() {
            enabled--;
        }
    }
}
