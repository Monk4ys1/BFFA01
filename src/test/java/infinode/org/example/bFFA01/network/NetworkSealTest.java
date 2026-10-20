package infinode.org.example.bFFA01.network;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkSealTest {

    private static final String SECRET = "cluster-secret-value";
    private static final long NOW = 1_700_000_000_000L;

    @Test
    void signedFrameRoundTripsAndHidesTheSecret() {
        String frame = NetworkSeal.encode(SECRET, "pod-a", "streak", "Steve|12", NOW);
        assertFalse(frame.contains(SECRET));
        NetworkSeal.Frame decoded = NetworkSeal.decode(SECRET, frame, NOW, true);
        assertNotNull(decoded);
        assertEquals("pod-a", decoded.id());
        assertEquals("streak", decoded.kind());
        assertEquals("Steve|12", decoded.body());
        assertEquals(NOW, decoded.time());
    }

    @Test
    void tamperStaleKindAndPluginChannelAreRejected() {
        String frame = NetworkSeal.encode(SECRET, "pod-a", "board", "1", NOW);
        char[] chars = frame.toCharArray();
        int flip = chars.length - 1;
        chars[flip] = chars[flip] == 'a' ? 'b' : 'a';
        assertNull(NetworkSeal.decode(SECRET, new String(chars), NOW, true));
        assertNull(NetworkSeal.decode(SECRET, frame, NOW + 31_000L, false));
        assertNull(NetworkSeal.decode(SECRET, NetworkSeal.encode(SECRET, "pod-a", "chat", "hi", NOW), NOW, false));
        String unsigned = NetworkSeal.encode("", "pod-a", "board", "1", NOW);
        assertNull(NetworkSeal.decode("", unsigned, NOW, true));
        assertNull(NetworkSeal.decode("", unsigned, NOW, false));
    }

    @Test
    void withSecretReplacesOnlyTheSecret() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("network.enabled", true);
        config.set("network.redis.host", "127.0.0.1");
        NetworkSettings settings = NetworkSettings.load(config).withSecret("shared-secret");
        assertEquals("shared-secret", settings.secret());
        assertTrue(settings.signed());
        assertEquals("127.0.0.1", settings.redisHost());
    }

    @Test
    void newlineSecretIsDropped() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("network.secret", "bad\nsecret");
        assertEquals("", NetworkSettings.load(config).secret());
        assertFalse(NetworkSettings.load(config).signed());
    }

    @Test
    void hmacRoundTripStaysUnderAMillisecondEach() {
        long start = System.nanoTime();
        String frame = null;
        int rounds = 2_000;
        for (int i = 0; i < rounds; i++) {
            frame = NetworkSeal.encode(SECRET, "pod-a", "board", "1", NOW);
            assertNotNull(NetworkSeal.decode(SECRET, frame, NOW, true));
        }
        long us = (System.nanoTime() - start) / 1_000L;
        System.out.println("BENCH NetworkSeal encode+decode x" + rounds + ": " + us + " us");
        assertTrue(us < 2_000_000L, "seal took " + us + " us");
        assertNotNull(frame);
    }
}
