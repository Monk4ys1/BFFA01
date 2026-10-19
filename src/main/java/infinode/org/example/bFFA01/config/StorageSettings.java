package infinode.org.example.bFFA01.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Storage selection from config.yml. Invalid SQL settings fall back to YAML.
 */
public final class StorageSettings {

    public enum Type {
        YAML,
        SQLITE,
        MYSQL
    }

    private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._-]{1,253}");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_]{1,64}");

    private final Type type;
    private final String sqliteFile;
    private final String mysqlHost;
    private final int mysqlPort;
    private final String mysqlDatabase;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final String mysqlParameters;

    private StorageSettings(Type type, String sqliteFile, String mysqlHost, int mysqlPort, String mysqlDatabase,
                            String mysqlUsername, String mysqlPassword, String mysqlParameters) {
        this.type = type;
        this.sqliteFile = sqliteFile;
        this.mysqlHost = mysqlHost;
        this.mysqlPort = mysqlPort;
        this.mysqlDatabase = mysqlDatabase;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.mysqlParameters = mysqlParameters;
    }

    public static StorageSettings yaml() {
        return new StorageSettings(Type.YAML, "data.db", "127.0.0.1", 3306, "bffa", "bffa", "", "");
    }

    public static StorageSettings resolve(FileConfiguration config) {
        if (config == null) {
            return yaml();
        }
        Type type = parse(config.getString("storage.type", "yaml"));
        String sqliteFile = config.getString("storage.sqlite.file", "data.db");
        String host = config.getString("storage.mysql.host", "127.0.0.1");
        int port = config.getInt("storage.mysql.port", 3306);
        String database = config.getString("storage.mysql.database", "bffa");
        String username = config.getString("storage.mysql.username", "bffa");
        String password = config.getString("storage.mysql.password", "");
        String parameters = config.getString("storage.mysql.parameters", "");
        if (type == Type.MYSQL && !validMysql(host, port, database)) {
            return yaml();
        }
        if (sqliteFile == null || sqliteFile.isBlank() || sqliteFile.contains("..") || sqliteFile.indexOf('/') >= 0 || sqliteFile.indexOf('\\') >= 0) {
            sqliteFile = "data.db";
        }
        return new StorageSettings(type, sqliteFile, host, port, database, username == null ? "" : username,
                password == null ? "" : password, sanitizeParameters(parameters));
    }

    public Type type() {
        return type;
    }

    public String sqliteFile() {
        return sqliteFile;
    }

    public String mysqlHost() {
        return mysqlHost;
    }

    public int mysqlPort() {
        return mysqlPort;
    }

    public String mysqlDatabase() {
        return mysqlDatabase;
    }

    public String mysqlUsername() {
        return mysqlUsername;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public String mysqlParameters() {
        return mysqlParameters;
    }

    private static Type parse(String raw) {
        if (raw == null) {
            return Type.YAML;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> Type.SQLITE;
            case "mysql", "mariadb" -> Type.MYSQL;
            default -> Type.YAML;
        };
    }

    static boolean validMysql(String host, int port, String database) {
        return host != null && HOST.matcher(host).matches()
                && port >= 1 && port <= 65535
                && database != null && DATABASE.matcher(database).matches();
    }

    private static String sanitizeParameters(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return "";
        }
        String value = parameters.trim();
        if (value.startsWith("?")) {
            value = value.substring(1);
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf(' ') >= 0) {
            return "";
        }
        return value;
    }
}
