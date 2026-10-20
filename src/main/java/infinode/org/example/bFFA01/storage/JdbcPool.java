package infinode.org.example.bFFA01.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * A few JDBC connections instead of one shared connection. Hikari is shaded
 * and relocated so it does not clash with another plugin's pool.
 */
final class JdbcPool implements AutoCloseable {

    private final HikariDataSource source;

    private JdbcPool(HikariDataSource source) {
        this.source = source;
    }

    static JdbcPool open(Driver driver, String url, Properties properties, boolean sqlite) {
        HikariConfig config = new HikariConfig();
        config.setDataSource(new DriverSource(driver, url, properties, sqlite));
        config.setPoolName("bffa");
        config.setMaximumPoolSize(sqlite ? 2 : 4);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(3_000L);
        config.setInitializationFailTimeout(3_000L);
        config.setAutoCommit(true);
        config.setRegisterMbeans(false);
        return new JdbcPool(new HikariDataSource(config));
    }

    Connection borrow() throws SQLException {
        return source.getConnection();
    }

    @Override
    public void close() {
        source.close();
    }

    private static final class DriverSource implements javax.sql.DataSource {

        private final Driver driver;
        private final String url;
        private final Properties properties;
        private final boolean sqlite;

        private DriverSource(Driver driver, String url, Properties properties, boolean sqlite) {
            this.driver = driver;
            this.url = url;
            this.properties = properties;
            this.sqlite = sqlite;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Properties copy = new Properties();
            copy.putAll(properties);
            Connection connection = driver.connect(url, copy);
            if (connection == null) {
                throw new SQLException("JDBC driver refused the URL");
            }
            if (sqlite) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA journal_mode=WAL");
                    statement.execute("PRAGMA synchronous=NORMAL");
                    statement.execute("PRAGMA busy_timeout=5000");
                }
            }
            return connection;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
