package infinode.org.example.bFFA01.network;

import infinode.org.example.bFFA01.ui.prefs.PrefCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisFramesTest {

    @Test
    void publishFrameCountsBytes() {
        String frame = RedisFrames.publish("bffa", "1|local|board|1|5");
        assertTrue(frame.startsWith("*3\r\n$7\r\nPUBLISH\r\n"));
        assertTrue(frame.contains("$4\r\nbffa\r\n"));
        assertTrue(frame.contains("1|local|board|1|5\r\n"));
    }

    @Test
    void serverIdKeepsLettersAndDigits() {
        assertEquals("lobby-1", ServerIds.clip("Lobby-1"));
        assertEquals("local", ServerIds.clip("!!!"));
    }

    @Test
    void prefRowRoundTrips() {
        String encoded = PrefCodec.encode(new PrefCodec.Values(true, false, true, true, "totem", false));
        assertEquals("1,0,1,1,totem,0", encoded);
        PrefCodec.Values values = PrefCodec.decode(encoded);
        assertTrue(values.damage());
        assertEquals("totem", values.effect());
        assertNull(PrefCodec.decode("1,1,1"));
    }
}
