package infinode.org.example.bFFA01.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerRuntimeTest {

    @Test
    void detectionPrefersTheMostSpecificServer() {
        assertEquals(ServerRuntime.Kind.FOLIA, ServerRuntime.detect(name -> name.contains("RegionizedServer")));
        assertEquals(ServerRuntime.Kind.PURPUR, ServerRuntime.detect(name -> name.contains("PurpurConfig")));
        assertEquals(ServerRuntime.Kind.PAPER, ServerRuntime.detect(name -> name.contains("PaperConfig")));
        assertEquals(ServerRuntime.Kind.SPIGOT, ServerRuntime.detect(name -> false));
    }

    @Test
    void versionsParseMajorMinorPatch() {
        assertArrayEquals(new int[] {1, 20, 4}, ServerRuntime.parseVersion("1.20.4-R0.1-SNAPSHOT"));
        assertArrayEquals(new int[] {26, 3, 0}, ServerRuntime.parseVersion("26.3"));
        assertArrayEquals(new int[] {0, 0, 0}, ServerRuntime.parseVersion("unknown"));
    }

    @Test
    void describeIncludesTheKind() {
        assertEquals("Paper 1.20.4", new ServerRuntime(ServerRuntime.Kind.PAPER, "1.20.4").describe());
    }
}
