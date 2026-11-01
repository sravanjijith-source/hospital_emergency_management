package hems.database;

import hems.util.DatabaseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Loads database configuration from environment variables (HEMS_DB_URL, HEMS_DB_USER,
 * HEMS_DB_PASSWORD) or a local config/db.properties file.
 * Never logs or prints passwords.
 */
public class DatabaseConfig {
    private static final Logger LOGGER = Logger.getLogger(DatabaseConfig.class.getName());

    public static final String ENV_URL = "HEMS_DB_URL";
    public static final String ENV_USER = "HEMS_DB_USER";
    public static final String ENV_PASSWORD = "HEMS_DB_PASSWORD";
    public static final String DEFAULT_CONFIG_FILE = "config/db.properties";

    private final String url;
    private final String user;
    private final String password;

    public DatabaseConfig(String url, String user, String password) {
        if (url == null || url.trim().isEmpty()) {
            throw new DatabaseException("Database URL is missing. Set " + ENV_URL + " or configure " + DEFAULT_CONFIG_FILE + ".");
        }
        if (user == null || user.trim().isEmpty()) {
            throw new DatabaseException("Database user is missing. Set " + ENV_USER + " or configure " + DEFAULT_CONFIG_FILE + ".");
        }
        this.url = url.trim();
        this.user = user.trim();
        this.password = (password != null) ? password : "";
    }

    /**
     * Resolves configuration by checking environment variables first, then local properties file.
     */
    public static DatabaseConfig load() {
        String url = System.getenv(ENV_URL);
        String user = System.getenv(ENV_USER);
        String password = System.getenv(ENV_PASSWORD);

        if (url != null && !url.trim().isEmpty() && user != null && !user.trim().isEmpty()) {
            LOGGER.info("Loaded database configuration from environment variables.");
            return new DatabaseConfig(url, user, password);
        }

        // Fall back to config/db.properties
        File file = new File(DEFAULT_CONFIG_FILE);
        if (file.exists() && file.isFile()) {
            Properties props = new Properties();
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
                String pUrl = props.getProperty("db.url");
                String pUser = props.getProperty("db.user");
                String pPass = props.getProperty("db.password");
                LOGGER.info("Loaded database configuration from " + DEFAULT_CONFIG_FILE);
                return new DatabaseConfig(pUrl, pUser, pPass);
            } catch (IOException e) {
                throw new DatabaseException("Could not read " + DEFAULT_CONFIG_FILE, e);
            }
        }

        throw new DatabaseException("Database credentials not configured. Please set "
                + ENV_URL + ", " + ENV_USER + ", and " + ENV_PASSWORD
                + ", or provide a valid " + DEFAULT_CONFIG_FILE + " file.");
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        // Safe representation without password
        return "DatabaseConfig[url=" + url + ", user=" + user + ", password=*****]";
    }
}
