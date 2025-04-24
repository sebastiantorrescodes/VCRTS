package db;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    
    // Default database connection parameters (used as fallback)
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/vcrts";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASSWORD = "";
    
    // Environment file path
    private static final String ENV_FILE = "db.env";
    private static final String ENV_DIR_PATH = System.getProperty("user.dir");

    // Connection parameters loaded from env file
    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;
    
    private static Connection connection = null;
    
    static {
        // Initialize connection parameters from environment file
        loadEnvironmentVariables();
    }
    
    /**
     * Loads database connection parameters from the environment file.
     * Falls back to default values if the file doesn't exist or properties are missing.
     */
    private static void loadEnvironmentVariables() {
        Properties properties = new Properties();
        Path envFilePath = Paths.get(ENV_DIR_PATH, ENV_FILE);
        
        // Check if env file exists
        if (Files.exists(envFilePath)) {
            try (FileInputStream fileInputStream = new FileInputStream(envFilePath.toFile())) {
                properties.load(fileInputStream);
                logger.info("Loaded database configuration from " + envFilePath);
                
                // Get properties from file with defaults as fallback
                dbUrl = properties.getProperty("DB_URL", DEFAULT_DB_URL);
                dbUser = properties.getProperty("DB_USER", DEFAULT_DB_USER);
                dbPassword = properties.getProperty("DB_PASSWORD", DEFAULT_DB_PASSWORD);
                
                logger.info("Database URL: " + dbUrl);
                logger.info("Database User: " + dbUser);
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load environment file: " + e.getMessage(), e);
                useDefaultValues();
            }
        } else {
            logger.warning("Environment file not found at: " + envFilePath + ". Creating a template file and using default values.");
            createTemplateEnvFile(envFilePath);
            useDefaultValues();
        }
    }
    
    /**
     * Creates a template environment file at the specified path.
     */
    private static void createTemplateEnvFile(Path path) {
        try {
            // Create template content with comments
            String templateContent = 
                "# Database Configuration\n" +
                "# Replace these values with your actual database credentials\n\n" +
                "DB_URL=" + DEFAULT_DB_URL + "\n" +
                "DB_USER=" + DEFAULT_DB_USER + "\n" +
                "DB_PASSWORD=\n";
            
            // Write to file
            Files.write(path, templateContent.getBytes());
            logger.info("Created template environment file at: " + path);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create template environment file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Use default values for database connection.
     */
    private static void useDefaultValues() {
        dbUrl = DEFAULT_DB_URL;
        dbUser = DEFAULT_DB_USER;
        dbPassword = DEFAULT_DB_PASSWORD;
        logger.warning("Using default database connection values");
    }
    
    /**
     * Get a database connection (singleton pattern)
     */
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Register the JDBC driver
                Class.forName("com.mysql.cj.jdbc.Driver");
                
                // Open the connection using values from env file
                connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
                logger.info("Database connection established");
            } catch (ClassNotFoundException e) {
                logger.log(Level.SEVERE, "JDBC Driver not found", e);
                throw new SQLException("JDBC Driver not found", e);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error connecting to database", e);
                throw e;
            }
        }
        return connection;
    }
    
    /**
     * Close the database connection
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing database connection", e);
            }
        }
    }
    
    /**
     * Closes the specified resources
     */
    public static void closeResources(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error closing resource", e);
                }
            }
        }
    }
    
    /**
     * Reload configuration from the environment file.
     * This can be called to refresh the configuration without restarting the application.
     */
    public static void reloadConfiguration() {
        loadEnvironmentVariables();
        
        // Close existing connection to force a new connection with updated parameters
        closeConnection();
        
        logger.info("Database configuration reloaded");
    }
}