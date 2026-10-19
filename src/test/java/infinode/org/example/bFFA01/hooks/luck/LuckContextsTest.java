package infinode.org.example.bFFA01.hooks.luck;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuckContextsTest {

    @Test
    void streakBucketsAndMapKeysStaySmall() {
        assertEquals("0", LuckContexts.bucket(0));
        assertEquals("1-4", LuckContexts.bucket(4));
        assertEquals("5-9", LuckContexts.bucket(9));
        assertEquals("10+", LuckContexts.bucket(40));
        assertEquals("arena1", LuckContexts.mapKey("Arena 1!"));
        assertEquals("none", LuckContexts.mapKey("!!!"));

        Map<String, String> values = LuckContexts.of("Arena", 6, true);
        assertEquals("arena", values.get("bffa-map"));
        assertEquals("5-9", values.get("bffa-streak"));
        assertEquals("true", values.get("bffa-bedrock"));
    }
}
