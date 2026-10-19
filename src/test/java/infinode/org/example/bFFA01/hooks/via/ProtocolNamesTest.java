package infinode.org.example.bFFA01.hooks.via;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolNamesTest {

    @Test
    void viaNameWinsAndUnknownProtocolsStayNumeric() {
        assertEquals("1.21.4", ProtocolNames.friendly(769, "1.21.4"));
        assertEquals("1.20.5-1.20.6", ProtocolNames.friendly(766, "UNKNOWN"));
        assertEquals("protocol 800", ProtocolNames.friendly(800, null));
        assertEquals("", ProtocolNames.friendly(-1, null));
    }
}
