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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * One JDBC connection for player rows. Callers serialize access.
 * MySQL writes claim a short lease so another server cannot overwrite a player
 * who is online here. SQLite uses the same statements with a local holder.
 */
public final class JdbcPlayerRepository implements AutoCloseable {

    private static final String COLUMNS = "id, player_name, kills, deaths, coins, upgrades, layout, "
            + "damage_dealt, damage_taken, life_dealt, life_taken, assists, streak, prefs";

    private static final String[] ADDED = {
            "damage_dealt BIGINT NOT NULL DEFAULT 0",
            "damage_taken BIGINT NOT NULL DEFAULT 0",
            "life_dealt BIGINT NOT NULL DEFAULT 0",
            "life_taken BIGINT NOT NULL DEFAULT 0",
            "assists INT NOT NULL DEFAULT 0",
            "streak INT NOT NULL DEFAULT 0",
            "holder VARCHAR(32) NOT NULL DEFAULT ''",
            "held_until BIGINT NOT NULL DEFAULT 0",
            "prefs VARCHAR(80) NOT NULL DEFAULT ''"
    };

    private final Connection connection;
    private final boolean sqlite;
    private String serverId = "local";
    private long leaseMillis = 30_000L;

    private JdbcPlayerRepository(Connection connection, boolean sqlite) {
        this.connection = connection;
        this.sqlite = sqlite;
    }

    public void lease(String serverId, long leaseMillis) {
        if (serverId != null && !serverId.isBlank()) {
            this.serverId = serverId.length() > 32 ? serverId.substring(0, 32) : serverId;
        }
        if (leaseMillis >= 5_000L) {
            this.leaseMillis = leaseMillis;
        }
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
            repository.migrate();
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
        repository.migrate();
        return repository;
    }

    public synchronized List<PlayerRecord> loadAll() throws SQLException {
        List<PlayerRecord> records = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT " + COLUMNS + " FROM bffa_player")) {
            while (rows.next()) {
                records.add(read(rows));
            }
        }
        return records;
    }

    public synchronized PlayerRecord loadOne(UUID id) throws SQLException {
        if (id == null) {
            return null;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM bffa_player WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return read(rows);
            }
        }
    }

    public synchronized List<KillEntry> topKills(int limit) throws SQLException {
        int capped = cap(limit);
        List<KillEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, player_name, kills FROM bffa_player ORDER BY kills DESC, player_name ASC LIMIT ?")) {
            statement.setInt(1, capped);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new KillEntry(UUID.fromString(rows.getString("id")), nameOf(rows), rows.getInt("kills")));
                }
            }
        }
        return entries;
    }

    public synchronized List<DamageEntry> topDamage(int limit) throws SQLException {
        int capped = cap(limit);
        List<DamageEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, player_name, damage_dealt FROM bffa_player ORDER BY damage_dealt DESC, player_name ASC LIMIT ?")) {
            statement.setInt(1, capped);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    entries.add(new DamageEntry(UUID.fromString(rows.getString("id")), nameOf(rows), rows.getLong("damage_dealt")));
                }
            }
        }
        return entries;
    }

    public synchronized void saveAll(Collection<PlayerRecord> records) throws SQLException {
        if (records == null || records.isEmpty()) {
            return;
        }
        boolean previous = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long until = System.currentTimeMillis() + leaseMillis;
            for (PlayerRecord record : records) {
                upsert(record, until);
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(previous);
        }
    }

    public synchronized void release(UUID id) throws SQLException {
        if (id == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET holder='', held_until=0 WHERE id=? AND holder=?")) {
            statement.setString(1, id.toString());
            statement.setString(2, serverId);
            statement.executeUpdate();
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
                + "layout VARCHAR(320) NOT NULL DEFAULT '',"
                + "damage_dealt BIGINT NOT NULL DEFAULT 0,"
                + "damage_taken BIGINT NOT NULL DEFAULT 0,"
                + "life_dealt BIGINT NOT NULL DEFAULT 0,"
                + "life_taken BIGINT NOT NULL DEFAULT 0,"
                + "assists INT NOT NULL DEFAULT 0,"
                + "streak INT NOT NULL DEFAULT 0,"
                + "holder VARCHAR(32) NOT NULL DEFAULT '',"
                + "held_until BIGINT NOT NULL DEFAULT 0,"
                + "prefs VARCHAR(80) NOT NULL DEFAULT ''"
                + ")";
        if (!sqlite) {
            ddl += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private void migrate() throws SQLException {
        Set<String> present = columns();
        try (Statement statement = connection.createStatement()) {
            for (String column : ADDED) {
                String name = column.substring(0, column.indexOf(' ')).toLowerCase(Locale.ROOT);
                if (present.contains(name)) {
                    continue;
                }
                statement.execute("ALTER TABLE bffa_player ADD COLUMN " + column);
            }
        }
    }

    private Set<String> columns() throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = sqlite ? "PRAGMA table_info(bffa_player)" : "SHOW COLUMNS FROM bffa_player";
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                names.add(rows.getString(sqlite ? "name" : "Field").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }

    private void upsert(PlayerRecord record, long until) throws SQLException {
        PlayerRecord copy = record.copy();
        long now = System.currentTimeMillis();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bffa_player SET player_name=?, kills=?, deaths=?, coins=?, upgrades=?, layout=?, "
                        + "damage_dealt=?, damage_taken=?, life_dealt=?, life_taken=?, assists=?, streak=?, prefs=?, "
                        + "holder=?, held_until=? WHERE id=? AND (holder=? OR holder='' OR held_until<?)")) {
            bind(update, copy, until);
            update.setString(16, copy.id().toString());
            update.setString(17, serverId);
            update.setLong(18, now);
            if (update.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bffa_player (player_name, kills, deaths, coins, upgrades, layout, "
                        + "damage_dealt, damage_taken, life_dealt, life_taken, assists, streak, prefs, holder, held_until, id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            bind(insert, copy, until);
            insert.setString(16, copy.id().toString());
            insert.executeUpdate();
        } catch (SQLException ex) {
            // Another server holds the row. Leave their numbers in place.
            if (!duplicate(ex)) {
                throw ex;
            }
        }
    }

    private void bind(PreparedStatement statement, PlayerRecord record, long until) throws SQLException {
        statement.setString(1, record.name());
        statement.setInt(2, record.kills());
        statement.setInt(3, record.deaths());
        statement.setInt(4, record.coins());
        statement.setString(5, PlayerRecord.joinUpgrades(record.upgrades()));
        statement.setString(6, PlayerRecord.joinLayout(record));
        statement.setLong(7, record.damageDealt());
        statement.setLong(8, record.damageTaken());
        statement.setLong(9, record.lifeDealt());
        statement.setLong(10, record.lifeTaken());
        statement.setInt(11, record.assists());
        statement.setInt(12, record.streak());
        statement.setString(13, clip(record.prefs(), 80));
        statement.setString(14, serverId);
        statement.setLong(15, until);
    }

    private static PlayerRecord read(ResultSet rows) throws SQLException {
        UUID id = UUID.fromString(rows.getString("id"));
        PlayerRecord record = new PlayerRecord(id);
        record.name(rows.getString("player_name"));
        record.loadCounts(rows.getInt("kills"), rows.getInt("deaths"), rows.getInt("coins"));
        record.loadCombat(rows.getLong("damage_dealt"), rows.getLong("damage_taken"),
                rows.getLong("life_dealt"), rows.getLong("life_taken"), rows.getInt("assists"), rows.getInt("streak"));
        record.prefs(rows.getString("prefs"));
        PlayerRecord.readUpgrades(record, rows.getString("upgrades"));
        PlayerRecord.readLayout(record, rows.getString("layout"));
        return record;
    }

    private static String nameOf(ResultSet rows) throws SQLException {
        String name = rows.getString("player_name");
        if (name == null || name.isBlank()) {
            return rows.getString("id").substring(0, 8);
        }
        return name;
    }

    private static int cap(int limit) {
        if (limit <= 0) {
            return 1;
        }
        return Math.min(limit, 500);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        if ("23000".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("unique");
    }
}
