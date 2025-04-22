package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    
    // Database connection parameters
    private static final String DB_URL = "jdbc:mysql://localhost:3306/vcrts"; // your own db url
    private static final String DB_USER = "root"; // your own connection name
    private static final String DB_PASSWORD = "your_password"; // use your own password for connection
    
    private static Connection connection = null;
    
    // Get a database connection (singleton pattern)
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                // Register the JDBC driver
                Class.forName("com.mysql.cj.jdbc.Driver");
                
                // Open the connection
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
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
    
//     Close the database connection
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
    
}