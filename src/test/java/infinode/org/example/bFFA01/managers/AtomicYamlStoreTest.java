package infinode.org.example.bFFA01.managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicYamlStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void corruptFileIsNotOverwritten() throws IOException {
        Path file = tempDir.resolve("data.yml");
        byte[] original = ":\n  - [\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);

        AtomicYamlStore store = AtomicYamlStore.open(file);

        assertTrue(store.hadLoadError());
        assertFalse(store.isWritable());
        assertThrows(IOException.class, () -> store.writeSnapshot("kills: 1\n"));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(0, tmpCount());
    }

    @Test
    void directoryIsNotReplaced() throws IOException {
        Path file = tempDir.resolve("data.yml");
        Files.createDirectory(file);
        AtomicYamlStore store = AtomicYamlStore.open(file);

        assertTrue(store.hadLoadError());
        assertThrows(IOException.class, () -> store.writeSnapshot("kills: 1\n"));
        assertTrue(Files.isDirectory(file));
    }

    @Test
    void snapshotReplacesFileAtomically() throws IOException {
        Path file = tempDir.resolve("data.yml");
        Files.writeString(file, "stats:\n  kills: 2\n");
        AtomicYamlStore store = AtomicYamlStore.open(file);

        assertTrue(store.isWritable());
        assertFalse(store.hadLoadError());
        assertEquals(2, store.configuration().getInt("stats.kills"));
        store.configuration().set("stats.kills", 3);
        store.writeSnapshot(store.saveToString());

        AtomicYamlStore again = AtomicYamlStore.open(file);
        assertEquals(3, again.configuration().getInt("stats.kills"));
        assertEquals(0, tmpCount());
        assertFalse(Files.exists(file.resolveSibling("data.yml.tmp")));
    }

    @Test
    void missingFileCanBeCreated() throws IOException {
        Path file = tempDir.resolve("nested").resolve("data.yml");
        AtomicYamlStore store = AtomicYamlStore.open(file);

        assertTrue(store.isWritable());
        store.configuration().set("a", 1);
        store.writeSnapshot(store.saveToString());

        assertTrue(Files.isRegularFile(file));
        assertEquals(1, AtomicYamlStore.open(file).configuration().getInt("a"));
        assertEquals(0, tmpCount());
    }

    private int tmpCount() throws IOException {
        try (var files = Files.walk(tempDir)) {
            return (int) files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count();
        }
    }
}
