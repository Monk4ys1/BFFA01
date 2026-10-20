package infinode.org.example.bFFA01.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InstanceRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitIdAndPodNameWinOverAGenericHostname() {
        assertEquals("lobby-1", InstanceIds.propose(Map.of("BFFA_SERVER_ID", "Lobby-1", "HOSTNAME", "paper")));
        assertEquals("bffa-0", InstanceIds.propose(Map.of("HOSTNAME", "paper", "POD_NAME", "bffa-0")));
        assertEquals("cursor", InstanceIds.propose(Map.of(), "cursor"));
        String generated = InstanceIds.propose(Map.of("HOSTNAME", "localhost"), "cursor");
        assertEquals(12, generated.length());
        assertNotEquals("localhost", generated);
        assertNotEquals("local", generated);
    }

    @Test
    void aLiveIdIsNotSharedAndAStaleIdCanBeTaken() throws Exception {
        Path file = tempDir.resolve("cluster.db");
        try (Connection first = open(file); Connection second = open(file)) {
            InstanceRegistry.Claim owned = InstanceRegistry.claim(first, true, "pod-a");
            InstanceRegistry.Claim other = InstanceRegistry.claim(second, true, "pod-a");
            assertEquals("pod-a", owned.id());
            assertNotEquals(owned.id(), other.id());

            ClusterSnapshot live = InstanceRegistry.beat(first, owned.id(), owned.token(), 4);
            assertEquals(2, live.servers());
            assertEquals(4, live.players());

            try (java.sql.Statement statement = first.createStatement()) {
                statement.execute("UPDATE bffa_instance SET beat=0 WHERE id='pod-a'");
            }
            InstanceRegistry.Claim recovered = InstanceRegistry.claim(second, true, "pod-a");
            assertEquals("pod-a", recovered.id());
            assertNotEquals(owned.token(), recovered.token());
        }
    }

    private static Connection open(Path file) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }
}
