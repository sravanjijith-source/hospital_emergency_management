package hems.database;

import hems.util.DatabaseException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe provider for JDBC database connections using DriverManager.
 * Student-friendly design: no heavyweight ORM or pool dependencies, zero leaks.
 */
public class ConnectionProvider {
    private static final Logger LOGGER = Logger.getLogger(ConnectionProvider.class.getName());

    private final DatabaseConfig config;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL Connector/J driver class not found on classpath", e);
        }
    }

    public ConnectionProvider(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig cannot be null.");
        }
        this.config = config;
    }

    /**
     * Opens a new database connection. Callers MUST use try-with-resources.
     */
    public Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(config.getUrl(), config.getUser(), config.getPassword());
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to connect to MySQL database at " + config.getUrl(), e);
            throw new DatabaseException("Cannot connect to MySQL database. Please verify the database is running and credentials are valid.", e);
        }
    }

    /**
     * Tests the database connection with a 3-second timeout.
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Connection test failed", e);
            return false;
        }
    }

    public DatabaseConfig getConfig() {
        return config;
    }
}
