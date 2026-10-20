package infinode.org.example.bFFA01.network;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Heartbeat rows in {@code bffa_instance}. A live row blocks another process
 * from taking that id. A stale row can be replaced after {@link #STALE_MILLIS}.
 */
public final class InstanceRegistry {

    public static final long STALE_MILLIS = 15_000L;

    public record Claim(String id, String token) {
    }

    private InstanceRegistry() {
    }

    public static void ensure(Connection connection, boolean sqlite) throws SQLException {
        String ddl = "CREATE TABLE IF NOT EXISTS bffa_instance ("
                + "id VARCHAR(32) NOT NULL PRIMARY KEY,"
                + "token VARCHAR(32) NOT NULL,"
                + "beat BIGINT NOT NULL,"
                + "started BIGINT NOT NULL,"
                + "players INT NOT NULL DEFAULT 0"
                + ")";
        if (!sqlite) {
            ddl += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    public static Claim claim(Connection connection, boolean sqlite, String preferred) throws SQLException {
        ensure(connection, sqlite);
        String token = token();
        String id = ServerIds.clip(preferred == null || preferred.isBlank() ? randomId() : preferred);
        long now = System.currentTimeMillis();
        for (int attempt = 0; attempt < 6; attempt++) {
            if (tryClaim(connection, id, token, now)) {
                return new Claim(id, token);
            }
            id = randomId();
            now = System.currentTimeMillis();
        }
        throw new SQLException("Could not claim a free instance id");
    }

    /**
     * @return the live set, or null when this token no longer owns {@code id}
     */
    public static ClusterSnapshot beat(Connection connection, String id, String token, int players) throws SQLException {
        long now = System.currentTimeMillis();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bffa_instance SET beat=?, players=? WHERE id=? AND token=?")) {
            update.setLong(1, now);
            update.setInt(2, Math.max(0, players));
            update.setString(3, id);
            update.setString(4, token);
            if (update.executeUpdate() == 0) {
                return null;
            }
        }
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM bffa_instance WHERE beat < ?")) {
            delete.setLong(1, now - STALE_MILLIS);
            delete.executeUpdate();
        }
        return read(connection, id, now);
    }

    public static void retire(Connection connection, String id, String token) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM bffa_instance WHERE id=? AND token=?")) {
            delete.setString(1, id);
            delete.setString(2, token);
            delete.executeUpdate();
        }
        try (PreparedStatement release = connection.prepareStatement(
                "UPDATE bffa_player SET holder='', held_until=0 WHERE holder=?")) {
            release.setString(1, id);
            release.executeUpdate();
        } catch (SQLException ex) {
            if (!missingTable(ex)) {
                throw ex;
            }
        }
    }

    public static ClusterSnapshot read(Connection connection, String selfId, long now) throws SQLException {
        List<ClusterSnapshot.Member> members = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, players FROM bffa_instance WHERE beat>=? ORDER BY id")) {
            select.setLong(1, now - STALE_MILLIS);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    members.add(new ClusterSnapshot.Member(rows.getString("id"), rows.getInt("players")));
                }
            }
        }
        members.sort(Comparator.comparing(ClusterSnapshot.Member::id));
        return new ClusterSnapshot(selfId, members);
    }

    private static boolean tryClaim(Connection connection, String id, String token, long now) throws SQLException {
        long stale = now - STALE_MILLIS;
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE bffa_instance SET token=?, beat=?, started=? WHERE id=? AND (token=? OR beat<?)")) {
            update.setString(1, token);
            update.setLong(2, now);
            update.setLong(3, now);
            update.setString(4, id);
            update.setString(5, token);
            update.setLong(6, stale);
            if (update.executeUpdate() > 0 && owns(connection, id, token)) {
                return true;
            }
        }
        if (fresh(connection, id, now)) {
            return false;
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO bffa_instance (id, token, beat, started, players) VALUES (?,?,?,?,0)")) {
            insert.setString(1, id);
            insert.setString(2, token);
            insert.setLong(3, now);
            insert.setLong(4, now);
            insert.executeUpdate();
            return true;
        } catch (SQLException ex) {
            if (!duplicate(ex)) {
                throw ex;
            }
        }
        return owns(connection, id, token);
    }

    private static boolean fresh(Connection connection, String id, long now) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT beat FROM bffa_instance WHERE id=?")) {
            select.setString(1, id);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() && rows.getLong("beat") >= now - STALE_MILLIS;
            }
        }
    }

    private static boolean owns(Connection connection, String id, String token) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT token FROM bffa_instance WHERE id=?")) {
            select.setString(1, id);
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() && token.equals(rows.getString("token"));
            }
        }
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String randomId() {
        return InstanceIds.random();
    }

    private static boolean duplicate(SQLException ex) {
        String state = ex.getSQLState();
        if ("23000".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("unique");
    }

    private static boolean missingTable(SQLException ex) {
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("no such table");
    }
}
