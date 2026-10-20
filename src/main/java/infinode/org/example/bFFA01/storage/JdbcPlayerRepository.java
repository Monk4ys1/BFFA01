package infinode.org.example.bFFA01.storage;

import infinode.org.example.bFFA01.config.StorageSettings;
import infinode.org.example.bFFA01.network.ClusterSnapshot;
import infinode.org.example.bFFA01.network.InstanceIds;
import infinode.org.example.bFFA01.network.InstanceRegistry;

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
    private String token = "";
    private long leaseMillis = 20_000L;
    private boolean retired;

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
        return open(settings, dataFolder, settings.type() == StorageSettings.Type.SQLITE ? 1 : 5);
    }

    public static JdbcPlayerRepository open(StorageSettings settings, Path dataFolder, int attempts) throws SQLException {
        int tries = Math.max(1, attempts);
        SQLException last = null;
        for (int attempt = 1; attempt <= tries; attempt++) {
            try {
                return connect(settings, dataFolder);
            } catch (SQLException ex) {
                last = ex;
                if (attempt == tries) {
                    break;
                }
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last;
    }

    private static JdbcPlayerRepository connect(StorageSettings settings, Path dataFolder) throws SQLException {
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
            repository.bootstrap();
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
        if (!settings.mysqlParameters().toLowerCase(Locale.ROOT).contains("connecttimeout")) {
            properties.setProperty("connectTimeout", "3000");
        }
        Connection connection = driver.connect(url, properties);
        if (connection == null) {
            throw new SQLException("JDBC driver refused the MySQL URL");
        }
        JdbcPlayerRepository repository = new JdbcPlayerRepository(connection, false);
        repository.bootstrap();
        return repository;
    }

    /**
     * Takes {@code preferred} when no live instance holds it. A collision,
     * including a copied server id, returns a different id.
     */
    public synchronized String claimInstance(String preferred) throws SQLException {
        InstanceRegistry.Claim claim = InstanceRegistry.claim(connection, sqlite, preferred);
        this.serverId = claim.id();
        this.token = claim.token();
        return claim.id();
    }

    public synchronized ClusterSnapshot heartbeat(int players) throws SQLException {
        if (token.isBlank()) {
            return ClusterSnapshot.alone(serverId);
        }
        ClusterSnapshot snapshot = InstanceRegistry.beat(connection, serverId, token, players);
        if (snapshot == null) {
            String next = claimInstance(InstanceIds.random());
            InstanceIds.adopt(next);
            snapshot = InstanceRegistry.beat(connection, serverId, token, players);
        }
        extendLeases();
        return snapshot == null ? ClusterSnapshot.alone(serverId) : snapshot;
    }

    public synchronized ClusterSnapshot cluster() throws SQLException {
        if (token.isBlank()) {
            return ClusterSnapshot.alone(serverId);
        }
        return InstanceRegistry.read(connection, serverId, System.currentTimeMillis());
    }

    public synchronized void retire() throws SQLException {
        if (retired || token.isBlank()) {
            retired = true;
            return;
        }
        InstanceRegistry.retire(connection, serverId, token);
        retired = true;
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

    public synchronized void applyDelta(CountDelta delta) throws SQLException {
        if (delta == null || delta.id() == null || !delta.any()) {
            return;
        }
        if (updateDelta(delta) > 0) {
            return;
        }
        try {
            insertDelta(delta);
        } catch (SQLException ex) {
            if (!duplicate(ex)) {
                throw ex;
            }
            updateDelta(delta);
        }
    }

    /**
     * @return 1 when this instance holds the row, 0 when the row does not exist yet, -1 when a live instance holds it
     */
    public synchronized int claimPlayer(UUID id) throws SQLException {
        if (id == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET holder=?, held_until=? WHERE id=? AND " + steal())) {
            statement.setString(1, serverId);
            statement.setLong(2, now + leaseMillis);
            statement.setString(3, id.toString());
            bindSteal(statement, 4, now);
            if (statement.executeUpdate() > 0) {
                return 1;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bffa_player WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? -1 : 0;
            }
        }
    }

    public synchronized boolean saveProfile(PlayerRecord record) throws SQLException {
        if (record == null || record.id() == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        PlayerRecord copy = record.copy();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET player_name=?, upgrades=?, layout=?, streak=?, prefs=?, holder=?, held_until=? "
                        + "WHERE id=? AND " + steal())) {
            statement.setString(1, copy.name());
            statement.setString(2, PlayerRecord.joinUpgrades(copy.upgrades()));
            statement.setString(3, PlayerRecord.joinLayout(copy));
            statement.setInt(4, copy.streak());
            statement.setString(5, clip(copy.prefs(), 80));
            statement.setString(6, serverId);
            statement.setLong(7, now + leaseMillis);
            statement.setString(8, copy.id().toString());
            bindSteal(statement, 9, now);
            if (statement.executeUpdate() > 0) {
                return true;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bffa_player (id, player_name, upgrades, layout, streak, prefs, holder, held_until) "
                        + "VALUES (?,?,?,?,?,?,?,?)")) {
            insert.setString(1, copy.id().toString());
            insert.setString(2, copy.name());
            insert.setString(3, PlayerRecord.joinUpgrades(copy.upgrades()));
            insert.setString(4, PlayerRecord.joinLayout(copy));
            insert.setInt(5, copy.streak());
            insert.setString(6, clip(copy.prefs(), 80));
            insert.setString(7, serverId);
            insert.setLong(8, now + leaseMillis);
            insert.executeUpdate();
            return true;
        } catch (SQLException ex) {
            if (!duplicate(ex)) {
                throw ex;
            }
        }
        return false;
    }

    public synchronized void resetCounters(UUID id) throws SQLException {
        if (id == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET kills=0, deaths=0, assists=0, streak=0, "
                        + "damage_dealt=0, damage_taken=0, life_dealt=0, life_taken=0 WHERE id=?")) {
            statement.setString(1, id.toString());
            statement.executeUpdate();
        }
    }

    public synchronized boolean clearLife(UUID id) throws SQLException {
        if (id == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET life_dealt=0, life_taken=0 WHERE id=? AND " + steal())) {
            statement.setString(1, id.toString());
            bindSteal(statement, 2, now);
            if (statement.executeUpdate() > 0) {
                return true;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM bffa_player WHERE id=?")) {
            statement.setString(1, id.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return !rows.next();
            }
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    private void bootstrap() throws SQLException {
        boolean locked = false;
        if (!sqlite) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT GET_LOCK('bffa_schema', 30)")) {
                locked = rows.next() && rows.getInt(1) == 1;
            }
            if (!locked) {
                throw new SQLException("Timed out waiting for schema setup");
            }
        }
        try {
            createTable();
            InstanceRegistry.ensure(connection, sqlite);
            migrate();
        } finally {
            if (locked) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SELECT RELEASE_LOCK('bffa_schema')");
                }
            }
        }
    }

    private void extendLeases() throws SQLException {
        long until = System.currentTimeMillis() + leaseMillis;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bffa_player SET held_until=? WHERE holder=?")) {
            statement.setLong(1, until);
            statement.setString(2, serverId);
            statement.executeUpdate();
        }
    }

    private int updateDelta(CountDelta delta) throws SQLException {
        String sql = "UPDATE bffa_player SET "
                + "kills = CASE WHEN kills + ? > 2147483647 THEN 2147483647 ELSE kills + ? END, "
                + "deaths = CASE WHEN deaths + ? > 2147483647 THEN 2147483647 ELSE deaths + ? END, "
                + "coins = CASE WHEN coins + ? < 0 THEN 0 WHEN coins + ? > 2147483647 THEN 2147483647 ELSE coins + ? END, "
                + "damage_dealt = damage_dealt + ?, damage_taken = damage_taken + ?, "
                + "life_dealt = life_dealt + ?, life_taken = life_taken + ?, "
                + "assists = CASE WHEN assists + ? > 2147483647 THEN 2147483647 ELSE assists + ? END "
                + "WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setInt(index++, delta.kills());
            statement.setInt(index++, delta.kills());
            statement.setInt(index++, delta.deaths());
            statement.setInt(index++, delta.deaths());
            statement.setInt(index++, delta.coins());
            statement.setInt(index++, delta.coins());
            statement.setInt(index++, delta.coins());
            statement.setLong(index++, delta.dealt());
            statement.setLong(index++, delta.taken());
            statement.setLong(index++, delta.lifeDealt());
            statement.setLong(index++, delta.lifeTaken());
            statement.setInt(index++, delta.assists());
            statement.setInt(index++, delta.assists());
            statement.setString(index, delta.id().toString());
            return statement.executeUpdate();
        }
    }

    private void insertDelta(CountDelta delta) throws SQLException {
        long now = System.currentTimeMillis();
        int coins = Math.max(0, delta.coins());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bffa_player (id, player_name, kills, deaths, coins, damage_dealt, damage_taken, "
                        + "life_dealt, life_taken, assists, holder, held_until) VALUES (?,'',?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, delta.id().toString());
            statement.setInt(2, Math.max(0, delta.kills()));
            statement.setInt(3, Math.max(0, delta.deaths()));
            statement.setInt(4, coins);
            statement.setLong(5, Math.max(0L, delta.dealt()));
            statement.setLong(6, Math.max(0L, delta.taken()));
            statement.setLong(7, Math.max(0L, delta.lifeDealt()));
            statement.setLong(8, Math.max(0L, delta.lifeTaken()));
            statement.setInt(9, Math.max(0, delta.assists()));
            statement.setString(10, serverId);
            statement.setLong(11, now + leaseMillis);
            statement.executeUpdate();
        }
    }

    private String steal() {
        return "(holder=? OR holder='' OR held_until<? OR NOT EXISTS ("
                + "SELECT 1 FROM bffa_instance i WHERE i.id=bffa_player.holder AND i.beat>=?))";
    }

    private void bindSteal(PreparedStatement statement, int index, long now) throws SQLException {
        statement.setString(index, serverId);
        statement.setLong(index + 1, now);
        statement.setLong(index + 2, now - InstanceRegistry.STALE_MILLIS);
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
        for (String column : ADDED) {
            String name = column.substring(0, column.indexOf(' ')).toLowerCase(Locale.ROOT);
            if (present.contains(name)) {
                continue;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE bffa_player ADD COLUMN " + column);
            } catch (SQLException ex) {
                if (!duplicateColumn(ex)) {
                    throw ex;
                }
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
                        + "holder=?, held_until=? WHERE id=? AND " + steal())) {
            bind(update, copy, until);
            update.setString(16, copy.id().toString());
            bindSteal(update, 17, now);
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

    private static boolean duplicateColumn(SQLException ex) {
        if (ex.getErrorCode() == 1060) {
            return true;
        }
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("duplicate column") || lower.contains("duplicate column name");
    }
}
