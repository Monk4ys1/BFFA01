package infinode.org.example.bFFA01.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Opens a bundled SQLite driver, or a MySQL/MariaDB driver dropped in the drivers folder.
 */
public final class DriverLoader {

    private DriverLoader() {
    }

    public static Driver open(Path driversDirectory, String... classNames) throws SQLException {
        Driver bundled = load(DriverLoader.class.getClassLoader(), classNames);
        if (bundled != null) {
            return bundled;
        }
        if (driversDirectory == null || !Files.isDirectory(driversDirectory)) {
            throw new SQLException("No JDBC driver found. SQLite is bundled; MySQL needs a jar in plugins/BFFA01/drivers/.");
        }
        Path root;
        try {
            root = driversDirectory.toRealPath();
        } catch (Exception ex) {
            throw new SQLException("Could not read the drivers folder", ex);
        }
        List<java.net.URL> urls = new ArrayList<>();
        try (var files = Files.newDirectoryStream(root, "*.jar")) {
            for (Path file : files) {
                Path real;
                try {
                    real = file.toRealPath();
                } catch (Exception ex) {
                    continue;
                }
                if (!real.startsWith(root) || !Files.isRegularFile(real)) {
                    continue;
                }
                urls.add(real.toUri().toURL());
            }
        } catch (Exception ex) {
            throw new SQLException("Could not read the drivers folder", ex);
        }
        if (urls.isEmpty()) {
            throw new SQLException("No JDBC driver jar in " + driversDirectory);
        }
        ClassLoader children = new java.net.URLClassLoader(urls.toArray(java.net.URL[]::new), DriverLoader.class.getClassLoader());
        Driver loaded = load(children, classNames);
        if (loaded == null) {
            throw new SQLException("None of the driver jars contained " + String.join(", ", classNames));
        }
        return loaded;
    }

    private static Driver load(ClassLoader loader, String... classNames) {
        if (classNames == null) {
            return null;
        }
        for (String className : classNames) {
            if (className == null || className.isBlank()) {
                continue;
            }
            try {
                Class<?> type = Class.forName(className, true, loader);
                return (Driver) type.getDeclaredConstructor().newInstance();
            } catch (Throwable ignored) {
                // Try the next driver name.
            }
        }
        return null;
    }
}
