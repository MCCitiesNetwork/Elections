package net.democracycraft.elections.database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.democracycraft.elections.Elections;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

import static net.democracycraft.elections.util.config.ConfigPaths.*;

/**
 * Thin MySQL manager for obtaining JDBC connections and running small tasks asynchronously.
 *
 * Contract:
 * - Reads configuration keys from plugin config: mysql.host, mysql.port, mysql.database, mysql.user, mysql.password, mysql.useSSL.
 * - Provides a lazily created JDBC connection (auto-reconnect on demand via getConnection()).
 * - Exposes a Gson instance for JSON serialization used by table helpers.
 * - Offers withConnection utility to safely execute code with the current connection.
 */
public class MySQLManager {

    private final Elections plugin;

    /** A Gson instance with null serialization enabled. */
    public final Gson gson = new GsonBuilder().serializeNulls().create();

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final boolean useSSL;

    private volatile Connection connection;
    private final Object connectionLock = new Object();

    public MySQLManager(Elections plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        var cfg = plugin.getConfig();
        this.host = Objects.requireNonNull(cfg.getString(MYSQL_HOST.getPath()), MYSQL_HOST.getPath());
        this.port = cfg.getInt(MYSQL_PORT.getPath());
        this.database = Objects.requireNonNull(cfg.getString(MYSQL_DATABASE.getPath()), MYSQL_DATABASE.getPath());
        this.user = Objects.requireNonNull(cfg.getString(MYSQL_USER.getPath()), MYSQL_USER.getPath());
        this.password = Objects.requireNonNull(cfg.getString(MYSQL_PASSWORD.getPath()), MYSQL_PASSWORD.getPath());
        this.useSSL = cfg.getBoolean(MYSQL_USE_SSL.getPath());
    }

    /** Ensures that the target database exists; creates it if missing. */
    public void ensureDatabaseExists() {
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=" + useSSL + "&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(serverUrl, user, password)) {
            try (var st = c.createStatement()) {
                st.execute("CREATE DATABASE IF NOT EXISTS `" + database + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to ensure database exists. Check credentials and privileges. Cause: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Opens a connection if none exists or it is closed.
     */
    public void connect() {
        try {
            if (connection != null && !connection.isClosed()) return;
        } catch (SQLException ignored) {}

        final String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + useSSL + "&autoReconnect=true&characterEncoding=UTF-8&serverTimezone=UTC";
        try {
            connection = DriverManager.getConnection(url, user, password);
            plugin.getLogger().info("Connected to MySQL");
        } catch (SQLException ex) {
            connection = null; // ensure null on failure
            plugin.getLogger().severe("Failed to connect to MySQL (" + url + ") as '" + user + "'. Cause: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Shorthand to ensure DB and connect. */
    public void setupDatabase() {
        ensureDatabaseExists();
        connect();
    }

    /**
     * Closes the active connection, if any.
     */
    public void disconnect() {
        try {
            if (connection != null) connection.close();
            plugin.getLogger().info("Disconnected from MySQL");
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to disconnect MySQL: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Guaranteed to return a live connection; reconnects as needed and throws if unavailable.
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            connect();
        }
        if (connection == null) {
            throw new IllegalStateException("MySQL connection is not available. Verify mysql.host/port/database/user/password, server reachability, and privileges.");
        }
        return connection;
    }

    /**
     * Thread-safe execution with a JDBC connection, returning a value.
     */
    public <R> R withConnection(IOFunction<Connection, R> fn) {
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
            } catch (RuntimeException e) {
                // Re-throw with clearer context for callers
                throw new RuntimeException("No MySQL connection available for operation.", e);
            }
            try {
                return fn.apply(conn);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Runs a task asynchronously on the Bukkit scheduler (useful for DB writes).
     */
    public void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /**
     * IO-like functional interface allowing lambdas that throw checked exceptions.
     */
    @FunctionalInterface
    public interface IOFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
