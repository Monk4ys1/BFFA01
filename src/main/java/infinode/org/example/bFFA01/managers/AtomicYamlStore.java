package infinode.org.example.bFFA01.managers;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Loads a YAML file and writes it by snapshot, temp file, and atomic rename.
 * A file that cannot be parsed is not writable, so a later save cannot replace it.
 */
public final class AtomicYamlStore {

    private final Path file;
    private final YamlConfiguration configuration;
    private final boolean writable;
    private final boolean loadError;

    private AtomicYamlStore(Path file, YamlConfiguration configuration, boolean writable, boolean loadError) {
        this.file = file;
        this.configuration = configuration;
        this.writable = writable;
        this.loadError = loadError;
    }

    public static AtomicYamlStore open(Path file) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (Files.exists(file)) {
            if (!Files.isRegularFile(file)) {
                return new AtomicYamlStore(file, configuration, false, true);
            }
            try {
                configuration.load(file.toFile());
            } catch (IOException | InvalidConfigurationException | RuntimeException ex) {
                return new AtomicYamlStore(file, new YamlConfiguration(), false, true);
            }
        }
        return new AtomicYamlStore(file, configuration, true, false);
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean hadLoadError() {
        return loadError;
    }

    public YamlConfiguration configuration() {
        return configuration;
    }

    public String saveToString() {
        return configuration.saveToString();
    }

    public void writeSnapshot(String yaml) throws IOException {
        if (!writable) {
            throw new IOException("Refusing to overwrite " + file.getFileName() + " after a load error");
        }
        if (yaml == null) {
            throw new IOException("Refusing to write a null snapshot of " + file.getFileName());
        }
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = absolute.resolveSibling(absolute.getFileName().toString() + ".tmp");
        try {
            byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            try {
                Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            syncDirectory(parent);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void syncDirectory(Path parent) {
        if (parent == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException ignored) {
            // Some filesystems cannot sync a directory. The rename itself is still atomic.
        }
    }
}
