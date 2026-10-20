package infinode.org.example.bFFA01.storage;

import infinode.org.example.bFFA01.config.StorageSettings;
import infinode.org.example.bFFA01.network.ClusterSnapshot;
import infinode.org.example.bFFA01.network.InstanceIds;
import infinode.org.example.bFFA01.network.InstanceRegistry;

import java.nio.file.Path;
import java.security.SecureRandom;
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
import java.util.Set;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Pooled JDBC access for player rows. One public call borrows one connection.
 * Nested calls on the same thread reuse it. MySQL writes claim a short lease
 * so another server cannot overwrite a player who is online here.
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

    private final JdbcPool pool;
    private final boolean sqlite;
    private final ThreadLocal<Connection> bound = new ThreadLocal<>();
    private final Object state = new Object();
    private String serverId = "local";
    private String token = "";
    private long leaseMillis = 20_000L;
    private volatile boolean retired;

    private JdbcPlayerRepository(JdbcPool pool, boolean sqlite) throws SQLException {
        this.pool = pool;
        this.sqlite = sqlite;
        try (Scope ignored = open()) {
            bootstrap();
        }
    }

    private Connection connection() {
        Connection current = bound.get();
        if (current == null) {
            throw new IllegalStateException("JDBC work is missing a connection");
        }
        return current;
    }

    private Scope open() throws SQLException {
        return new Scope();
    }

    private final class Scope implements AutoCloseable {
        private final boolean nested;
        private final Connection owned;

        private Scope() throws SQLException {
            if (bound.get() != null) {
                nested = true;
                owned = null;
                return;
            }
            nested = false;
            owned = pool.borrow();
            bound.set(owned);
        }

        @Override
        public void close() {
            if (nested) {
                return;
            }
            bound.remove();
            if (owned != null) {
                try {
                    owned.close();
                } catch (SQLException ignored) {
                    // The pool drops a broken connection.
                }
            }
        }
    }

    public void lease(String serverId, long leaseMillis) {
        synchronized (state) {
            if (serverId != null && !serverId.isBlank()) {
                this.serverId = serverId.length() > 32 ? serverId.substring(0, 32) : serverId;
            }
            if (leaseMillis >= 5_000L) {
                this.leaseMillis = leaseMillis;
            }
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
            String url = "jdbc:sqlite:" + file;
            try (Connection probe = driver.connect(url, properties)) {
                if (probe == null) {
                    throw new SQLException("SQLite driver refused " + file.getFileName());
                }
            }
            return new JdbcPlayerRepository(JdbcPool.open(driver, url, properties, true), true);
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
        try (Connection probe = driver.connect(url, properties)) {
            if (probe == null) {
                throw new SQLException("JDBC driver refused the MySQL URL");
            }
        }
        return new JdbcPlayerRepository(JdbcPool.open(driver, url, properties, false), false);
    }

    /**
     * Takes {@code preferred} when no live instance holds it. A collision,
     * including a copied server id, returns a different id.
     */
    public String claimInstance(String preferred) throws SQLException {
        try (Scope scope = open()) {
            InstanceRegistry.Claim claim = InstanceRegistry.claim(connection(), sqlite, preferred);
            synchronized (state) {
                this.serverId = claim.id();
                this.token = claim.token();
            }
            return claim.id();
        }
    }

    public ClusterSnapshot heartbeat(int players) throws SQLException {
        String id;
        String leaseToken;
        synchronized (state) {
            id = serverId;
            leaseToken = token;
        }
        if (leaseToken.isBlank()) {
            return ClusterSnapshot.alone(id);
        }
        try (Scope scope = open()) {
            ClusterSnapshot snapshot = InstanceRegistry.beat(connection(), id, leaseToken, players);
            if (snapshot == null) {
                String next = claimInstance(InstanceIds.random());
                InstanceIds.adopt(next);
                synchronized (state) {
                    id = serverId;
                    leaseToken = token;
                }
                snapshot = InstanceRegistry.beat(connection(), id, leaseToken, players);
            }
            extendLeases();
            return snapshot == null ? ClusterSnapshot.alone(id) : snapshot;
        }
    }

    public ClusterSnapshot cluster() throws SQLException {
        String id;
        String leaseToken;
        synchronized (state) {
            id = serverId;
            leaseToken = token;
        }
        if (leaseToken.isBlank()) {
            return ClusterSnapshot.alone(id);
        }
        try (Scope scope = open()) {
            return InstanceRegistry.read(connection(), id, System.currentTimeMillis());
        }
    }

    public void retire() throws SQLException {
        String id;
        String leaseToken;
        synchronized (state) {
            if (retired || token.isBlank()) {
                retired = true;
                return;
            }
            id = serverId;
            leaseToken = token;
        }
        try (Scope scope = open()) {
            InstanceRegistry.retire(connection(), id, leaseToken);
        }
        synchronized (state) {
            retired = true;
        }
    }

    public List<PlayerRecord> loadAll() throws SQLException {
        try (Scope scope = open()) {
            List<PlayerRecord> records = new ArrayList<>();
            try (Statement statement = connection().createStatement();
                 ResultSet rows = statement.executeQuery("SELECT " + COLUMNS + " FROM bffa_player")) {
                while (rows.next()) {
                    records.add(read(rows));
                }
            }
            return records;
        }
    }

    public PlayerRecord loadOne(UUID id) throws SQLException {
        if (id == null) {
            return null;
        }
        try (Scope scope = open()) {
            try (PreparedStatement statement = connection().prepareStatement(
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
    }

    public List<KillEntry> topKills(int limit) throws SQLException {
        int capped = cap(limit);
        try (Scope scope = open()) {
            List<KillEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection().prepareStatement(
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
    }

    public List<DamageEntry> topDamage(int limit) throws SQLException {
        int capped = cap(limit);
        try (Scope scope = open()) {
            List<DamageEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection().prepareStatement(
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
    }

    /**
     * One page of rows, highest kills first. {@code limit} is clamped to 1..100.
     */
    public List<PlayerRecord> loadPage(int offset, int limit) throws SQLException {
        int capped = Math.min(Math.max(limit, 1), 100);
        int start = Math.max(0, offset);
        try (Scope scope = open()) {
            List<PlayerRecord> records = new ArrayList<>();
            try (PreparedStatement statement = connection().prepareStatement(
                    "SELECT " + COLUMNS + " FROM bffa_player ORDER BY kills DESC, player_name ASC LIMIT ? OFFSET ?")) {
                statement.setInt(1, capped);
                statement.setInt(2, start);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        records.add(read(rows));
                    }
                }
            }
            return records;
        }
    }

    public void saveAll(Collection<PlayerRecord> records) throws SQLException {
        if (records == null || records.isEmpty()) {
            return;
        }
        try (Scope scope = open()) {
            boolean previous = connection().getAutoCommit();
            connection().setAutoCommit(false);
            try {
                long until = System.currentTimeMillis() + leaseMillis;
                for (PlayerRecord record : records) {
                    upsert(record, until);
                }
                connection().commit();
            } catch (SQLException ex) {
                connection().rollback();
                throw ex;
            } finally {
                connection().setAutoCommit(previous);
            }
        }
    }

    public void release(UUID id) throws SQLException {
        if (id == null) {
            return;
        }
        try (Scope scope = open()) {
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE bffa_player SET holder='', held_until=0 WHERE id=? AND holder=?")) {
                statement.setString(1, id.toString());
                statement.setString(2, holder());
                statement.executeUpdate();
            }
        }
    }

    public void applyDelta(CountDelta delta) throws SQLException {
        if (delta == null || delta.id() == null || !delta.any()) {
            return;
        }
        try (Scope scope = open()) {
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
    }

    /**
     * Inserts {@code bufferId} and the counter deltas in one transaction.
     *
     * @return false when this id was already committed, so a retried file does not add the counters again
     */
    public boolean applyBuffer(String bufferId, List<CountDelta> deltas, List<PlayerRecord> profiles) throws SQLException {
        return applyBuffer(bufferId, deltas, profiles, Set.of(), Set.of());
    }

    /**
     * @param resetIds rows whose counters are set to the delta values, inside the same transaction
     * @param lifeResetIds rows whose current-life damage is cleared before the delta is added
     */
    public boolean applyBuffer(String bufferId, List<CountDelta> deltas, List<PlayerRecord> profiles,
                               Set<UUID> resetIds, Set<UUID> lifeResetIds) throws SQLException {
        if (bufferId == null || bufferId.isBlank() || bufferId.length() > 36) {
            throw new SQLException("buffer id is missing");
        }
        try (Scope scope = open()) {
            Connection conn = connection();
            boolean previous = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO bffa_buffer (id, applied) VALUES (?, ?)")) {
                    insert.setString(1, bufferId);
                    insert.setLong(2, System.currentTimeMillis());
                    insert.executeUpdate();
                } catch (SQLException ex) {
                    conn.rollback();
                    if (duplicate(ex)) {
                        return false;
                    }
                    throw ex;
                }
                if (resetIds != null) {
                    for (UUID id : resetIds) {
                        resetCounters(id);
                    }
                }
                if (lifeResetIds != null) {
                    for (UUID id : lifeResetIds) {
                        if (resetIds == null || !resetIds.contains(id)) {
                            clearLife(id);
                        }
                    }
                }
                if (deltas != null) {
                    for (CountDelta delta : deltas) {
                        applyDelta(delta);
                    }
                }
                if (profiles != null) {
                    for (PlayerRecord profile : profiles) {
                        try {
                            saveProfile(profile);
                        } catch (SQLException ignored) {
                            // A refused lease must not undo the counters.
                        }
                    }
                }
                conn.commit();
                return true;
            } catch (SQLException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                    // The caller retries the same buffer id.
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(previous);
                } catch (SQLException ignored) {
                    // The pool resets auto-commit when the connection returns.
                }
            }
        }
    }

    /**
     * Returns the cluster secret. An existing row is never replaced. An empty
     * table stores {@code preferred} when it is usable, otherwise a generated value.
     * MySQL takes {@code GET_LOCK('bffa_secret')} for the read-or-insert.
     */
    public String storedSecret(String preferred) throws SQLException {
        try (Scope scope = open()) {
            boolean locked = false;
            if (!sqlite) {
                try (Statement statement = connection().createStatement();
                     ResultSet rows = statement.executeQuery("SELECT GET_LOCK('bffa_secret', 10)")) {
                    locked = rows.next() && rows.getInt(1) == 1;
                }
                if (!locked) {
                    throw new SQLException("Timed out waiting for the cluster secret");
                }
            }
            try {
                String existing = readSecret();
                if (!existing.isEmpty()) {
                    return existing;
                }
                String chosen = usableSecret(preferred) ? clip(preferred.trim(), 128) : randomSecret();
                try (PreparedStatement insert = connection().prepareStatement(
                        "INSERT INTO bffa_secret (id, secret) VALUES ('cluster', ?)")) {
                    insert.setString(1, chosen);
                    insert.executeUpdate();
                    return chosen;
                } catch (SQLException ex) {
                    if (!duplicate(ex)) {
                        throw ex;
                    }
                }
                return readSecret();
            } finally {
                if (locked) {
                    try (Statement statement = connection().createStatement()) {
                        statement.execute("SELECT RELEASE_LOCK('bffa_secret')");
                    }
                }
            }
        }
    }

    /**
     * @return 1 when this instance holds the row, 0 when the row does not exist yet, -1 when a live instance holds it
     */
    public int claimPlayer(UUID id) throws SQLException {
        if (id == null) {
            return 0;
        }
        try (Scope scope = open()) {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE bffa_player SET holder=?, held_until=? WHERE id=? AND " + steal())) {
                statement.setString(1, holder());
                statement.setLong(2, now + leaseMillis);
                statement.setString(3, id.toString());
                bindSteal(statement, 4, now);
                if (statement.executeUpdate() > 0) {
                    return 1;
                }
            }
            try (PreparedStatement statement = connection().prepareStatement(
                    "SELECT id FROM bffa_player WHERE id=?")) {
                statement.setString(1, id.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? -1 : 0;
                }
            }
        }
    }

    public boolean saveProfile(PlayerRecord record) throws SQLException {
        if (record == null || record.id() == null) {
            return false;
        }
        try (Scope scope = open()) {
            long now = System.currentTimeMillis();
            PlayerRecord copy = record.copy();
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE bffa_player SET player_name=?, upgrades=?, layout=?, streak=?, prefs=?, holder=?, held_until=? "
                            + "WHERE id=? AND " + steal())) {
                statement.setString(1, copy.name());
                statement.setString(2, PlayerRecord.joinUpgrades(copy.upgrades()));
                statement.setString(3, PlayerRecord.joinLayout(copy));
                statement.setInt(4, copy.streak());
                statement.setString(5, clip(copy.prefs(), 80));
                statement.setString(6, holder());
                statement.setLong(7, now + leaseMillis);
                statement.setString(8, copy.id().toString());
                bindSteal(statement, 9, now);
                if (statement.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement insert = connection().prepareStatement(
                    "INSERT INTO bffa_player (id, player_name, upgrades, layout, streak, prefs, holder, held_until) "
                            + "VALUES (?,?,?,?,?,?,?,?)")) {
                insert.setString(1, copy.id().toString());
                insert.setString(2, copy.name());
                insert.setString(3, PlayerRecord.joinUpgrades(copy.upgrades()));
                insert.setString(4, PlayerRecord.joinLayout(copy));
                insert.setInt(5, copy.streak());
                insert.setString(6, clip(copy.prefs(), 80));
                insert.setString(7, holder());
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
    }

    public void resetCounters(UUID id) throws SQLException {
        if (id == null) {
            return;
        }
        try (Scope scope = open()) {
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE bffa_player SET kills=0, deaths=0, assists=0, streak=0, "
                            + "damage_dealt=0, damage_taken=0, life_dealt=0, life_taken=0 WHERE id=?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }
        }
    }

    public boolean clearLife(UUID id) throws SQLException {
        if (id == null) {
            return true;
        }
        try (Scope scope = open()) {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection().prepareStatement(
                    "UPDATE bffa_player SET life_dealt=0, life_taken=0 WHERE id=? AND " + steal())) {
                statement.setString(1, id.toString());
                bindSteal(statement, 2, now);
                if (statement.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement statement = connection().prepareStatement(
                    "SELECT id FROM bffa_player WHERE id=?")) {
                statement.setString(1, id.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    return !rows.next();
                }
            }
        }
    }

    @Override
    public void close() {
        retired = true;
        pool.close();
    }

    private void bootstrap() throws SQLException {
        boolean locked = false;
        if (!sqlite) {
            try (Statement statement = connection().createStatement();
                 ResultSet rows = statement.executeQuery("SELECT GET_LOCK('bffa_schema', 30)")) {
                locked = rows.next() && rows.getInt(1) == 1;
            }
            if (!locked) {
                throw new SQLException("Timed out waiting for schema setup");
            }
        }
        try {
            createTable();
            InstanceRegistry.ensure(connection(), sqlite);
            migrate();
            indexes();
        } finally {
            if (locked) {
                try (Statement statement = connection().createStatement()) {
                    statement.execute("SELECT RELEASE_LOCK('bffa_schema')");
                }
            }
        }
    }

    private String holder() {
        synchronized (state) {
            return serverId;
        }
    }

    private void extendLeases() throws SQLException {
        long until = System.currentTimeMillis() + leaseMillis;
        try (PreparedStatement statement = connection().prepareStatement(
                "UPDATE bffa_player SET held_until=? WHERE holder=?")) {
            statement.setLong(1, until);
            statement.setString(2, holder());
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
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
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
        try (PreparedStatement statement = connection().prepareStatement(
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
            statement.setString(10, holder());
            statement.setLong(11, now + leaseMillis);
            statement.executeUpdate();
        }
    }

    private String steal() {
        return "(holder=? OR holder='' OR held_until<? OR NOT EXISTS ("
                + "SELECT 1 FROM bffa_instance i WHERE i.id=bffa_player.holder AND i.beat>=?))";
    }

    private void bindSteal(PreparedStatement statement, int index, long now) throws SQLException {
        statement.setString(index, holder());
        statement.setLong(index + 1, now);
        statement.setLong(index + 2, now - InstanceRegistry.STALE_MILLIS);
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
        String buffer = "CREATE TABLE IF NOT EXISTS bffa_buffer ("
                + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "applied BIGINT NOT NULL DEFAULT 0"
                + ")";
        String secret = "CREATE TABLE IF NOT EXISTS bffa_secret ("
                + "id VARCHAR(16) NOT NULL PRIMARY KEY,"
                + "secret VARCHAR(128) NOT NULL"
                + ")";
        if (!sqlite) {
            ddl += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            buffer += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            secret += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        try (Statement statement = connection().createStatement()) {
            statement.execute(ddl);
            statement.execute(buffer);
            statement.execute(secret);
        }
    }

    private void migrate() throws SQLException {
        Set<String> present = columns();
        for (String column : ADDED) {
            String name = column.substring(0, column.indexOf(' ')).toLowerCase(Locale.ROOT);
            if (present.contains(name)) {
                continue;
            }
            try (Statement statement = connection().createStatement()) {
                statement.execute("ALTER TABLE bffa_player ADD COLUMN " + column);
            } catch (SQLException ex) {
                if (!duplicateColumn(ex)) {
                    throw ex;
                }
            }
        }
    }

    private void indexes() throws SQLException {
        ensureIndex("bffa_kills", "kills, player_name");
        ensureIndex("bffa_damage", "damage_dealt, player_name");
    }

    private void ensureIndex(String name, String columns) throws SQLException {
        try (Statement statement = connection().createStatement()) {
            statement.execute("CREATE INDEX " + name + " ON bffa_player (" + columns + ")");
        } catch (SQLException ex) {
            if (!duplicateIndex(ex)) {
                throw ex;
            }
        }
    }

    private Set<String> columns() throws SQLException {
        Set<String> names = new HashSet<>();
        String sql = sqlite ? "PRAGMA table_info(bffa_player)" : "SHOW COLUMNS FROM bffa_player";
        try (Statement statement = connection().createStatement();
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
        try (PreparedStatement update = connection().prepareStatement(
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
        try (PreparedStatement insert = connection().prepareStatement(
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
        statement.setString(14, holder());
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

    private String readSecret() throws SQLException {
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT secret FROM bffa_secret WHERE id='cluster'")) {
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return "";
                }
                String secret = rows.getString(1);
                return secret == null ? "" : secret;
            }
        }
    }

    private static boolean usableSecret(String preferred) {
        if (preferred == null) {
            return false;
        }
        String trimmed = preferred.trim();
        return !trimmed.isEmpty() && trimmed.indexOf('\n') < 0 && trimmed.indexOf('\r') < 0;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", value));
        }
        return hex.toString();
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

    private static boolean duplicateIndex(SQLException ex) {
        if (ex.getErrorCode() == 1061) {
            return true;
        }
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("already exists") || lower.contains("duplicate key name");
    }
}
