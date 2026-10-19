package infinode.org.example.bFFA01.storage;

import infinode.org.example.bFFA01.config.StorageSettings;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * One JDBC connection for player rows. Callers serialize access.
 */
public final class JdbcPlayerRepository implements AutoCloseable {

    private final Connection connection;
    private final boolean sqlite;

    private JdbcPlayerRepository(Connection connection, boolean sqlite) {
        this.connection = connection;
        this.sqlite = sqlite;
    }

    public static JdbcPlayerRepository open(StorageSettings settings, Path dataFolder) throws SQLException {
        if (settings.type() == StorageSettings.Type.SQLITE) {
            Path file = dataFolder.resolve(settings.sqliteFile()).toAbsolutePath();
            Driver driver = DriverLoader.open(dataFolder.resolve("drivers"), "org.sqlite.JDBC");
            Properties properties = new Properties();
            Connection connection = driver.connect("jdbc:sqlite:" + file, properties);
            if (connection == null) {
                throw new SQLException("SQLite driver refused " + file.getFileName());
            }
            JdbcPlayerRepository repository = new JdbcPlayerRepository(connection, true);
            repository.prepareSqlite();
            repository.createTable();
            return repository;
        }
        Driver driver = DriverLoader.open(dataFolder.resolve("drivers"),
                "org.mariadb.jdbc.Driver",
                "com.mysql.cj.jdbc.Driver");
        boolean mariadb = driver.getClass().getName().contains("mariadb");
        String url = (mariadb ? "jdbc:mariadb://" : "jdbc:mysql://")
                + settings.mysqlHost() + ":" + settings.mysqlPort() + "/" + settings.mysqlDatabase();
        if (!settings.mysqlParameters().isEmpty()) {
            url += "?" + settings.mysqlParameters();
        }
        Properties properties = new Properties();
        properties.setProperty("user", settings.mysqlUsername());
        properties.setProperty("password", settings.mysqlPassword());
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("JDBC driver refused the MySQL URL");
        }
        JdbcPlayerRepository repository = new JdbcPlayerRepository(connection, false);
        repository.createTable();
        return repository;
    }

    public synchronized List<PlayerRecord> loadAll() throws SQLException {
        List<PlayerRecord> records = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, player_name, kills, deaths, coins, upgrades, layout FROM bffa_player")) {
            while (rows.next()) {
                records.add(read(rows));
            }
        }
        return records;
    }

    public synchronized void saveAll(Collection<PlayerRecord> records) throws SQLException {
        if (records == null || records.isEmpty()) {
            return;
        }
        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (PlayerRecord record : records) {
                upsert(record);
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private void prepareSqlite() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private void createTable() throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS bffa_player ("
                + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "player_name VARCHAR(16) NOT NULL DEFAULT '',"
                + "kills INT NOT NULL DEFAULT 0,"
                + "deaths INT NOT NULL DEFAULT 0,"
                + "coins INT NOT NULL DEFAULT 0,"
                + "upgrades VARCHAR(512) NOT NULL DEFAULT '',"
                + "layout VARCHAR(320) NOT NULL DEFAULT ''"
                + ")";
        if (!sqlite) {
            ddl += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private void upsert(PlayerRecord record) throws SQLException {
        PlayerRecord copy = record.copy();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bffa_player SET player_name=?, kills=?, deaths=?, coins=?, upgrades=?, layout=? WHERE id=?")) {
            bind(update, copy, false);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bffa_player (player_name, kills, deaths, coins, upgrades, layout, id) VALUES (?,?,?,?,?,?,?)")) {
            bind(insert, copy, false);
            insert.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, PlayerRecord record, boolean ignored) throws SQLException {
        statement.setString(1, record.name());
        statement.setInt(2, record.kills());
        statement.setInt(3, record.deaths());
        statement.setInt(4, record.coins());
        statement.setString(5, PlayerRecord.joinUpgrades(record.upgrades()));
        statement.setString(6, PlayerRecord.joinLayout(record));
        statement.setString(7, record.id().toString());
    }

    private static PlayerRecord read(ResultSet rows) throws SQLException {
        UUID id = UUID.fromString(rows.getString("id"));
        PlayerRecord record = new PlayerRecord(id);
        record.name(rows.getString("player_name"));
        record.loadCounts(rows.getInt("kills"), rows.getInt("deaths"), rows.getInt("coins"));
        PlayerRecord.readUpgrades(record, rows.getString("upgrades"));
        PlayerRecord.readLayout(record, rows.getString("layout"));
        return record;
    }
}
