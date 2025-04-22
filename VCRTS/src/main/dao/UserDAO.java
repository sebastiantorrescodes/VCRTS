//package dao;
//
//import db.FileManager;
//import java.util.logging.*;
//import models.User;
//import java.util.*;
//
//public class UserDAO {
//    private static final Logger logger = Logger.getLogger(UserDAO.class.getName());
//    private static final String USERS_FILE = "users.txt";
//    private static final String DELIMITER = "\\|";
//    private static final String SEPARATOR = "|";
//
//    /**
//     * Converts a User object to a line of text for storage.
//     */
//    private String userToLine(User user) {
//        return user.getUserId() + SEPARATOR +
//                user.getFullName() + SEPARATOR +
//                user.getEmail() + SEPARATOR +
//                user.getRolesAsString() + SEPARATOR +
//                user.getPassword();
//    }
//
//    /**
//     * Converts a line of text to a User object.
//     */
//    private User lineToUser(String line) {
//        String[] parts = line.split(DELIMITER);
//        if (parts.length < 5) {
//            logger.warning("Invalid user data format: " + line);
//            return null;
//        }
//
//        User user = new User(
//                parts[1], // fullName
//                parts[2], // email
//                parts[3], // roles
//                parts[4]  // password
//        );
//        user.setUserId(Integer.parseInt(parts[0]));
//        return user;
//    }
//
//    /**
//     * Adds a new user to the file.
//     * The provided User object's passwordHash field should contain the plain-text password.
//     * This method will hash it before storing.
//     *
//     * @param user A User object with plain-text password in its passwordHash field.
//     * @return true if the user is added successfully; false otherwise.
//     */
//    public boolean addUser(User user) {
//        String Password = user.getPassword();
//        user.setPassword(Password);
//
//        // Generate a unique ID for the new user
//        int userId = FileManager.generateUniqueNumericId(USERS_FILE);
//        user.setUserId(userId);
//
//        String userLine = userToLine(user);
//        return FileManager.appendLine(USERS_FILE, userLine);
//    }
//
//    /**
//     * Retrieves a user from the file by user ID.
//     *
//     * @param userId The user ID.
//     * @return A User object if found; null otherwise.
//     */
//    public User getUserById(int userId) {
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null && user.getUserId() == userId) {
//                return user;
//            }
//        }
//        return null;
//    }
//
//    /**
//     * Retrieves all users from the file.
//     *
//     * @return A List of User objects.
//     */
//    public List<User> getAllUsers() {
//        List<User> users = new ArrayList<>();
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null) {
//                users.add(user);
//            }
//        }
//
//        return users;
//    }
//
//    /**
//     * Updates an existing user's details (except the password).
//     *
//     * @param user A User object with updated information.
//     * @return true if the update is successful; false otherwise.
//     */
//    public boolean updateUser(User user) {
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//        List<String> updatedLines = new ArrayList<>();
//        boolean updated = false;
//
//        for (String line : lines) {
//            User existingUser = lineToUser(line);
//            if (existingUser != null && existingUser.getUserId() == user.getUserId()) {
//                // Keep the existing password hash
//                user.setPassword(existingUser.getPassword());
//                updatedLines.add(userToLine(user));
//                updated = true;
//            } else {
//                updatedLines.add(line);
//            }
//        }
//
//        return updated && FileManager.writeAllLines(USERS_FILE, updatedLines);
//    }
//
//    /**
//     * Updates a user's password.
//     *
//     * @param userId The user's ID as a string.
//     * @param newPlainPassword The new plain-text password.
//     * @return true if the update is successful; false otherwise.
//     */
//    public boolean updatePassword(String userId, String newPlainPassword) {
//        int id;
//        try {
//            id = Integer.parseInt(userId);
//        } catch (NumberFormatException e) {
//            logger.log(Level.WARNING, "Invalid user ID format: " + userId);
//            return false;
//        }
//
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//        List<String> updatedLines = new ArrayList<>();
//        boolean updated = false;
//
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null && user.getUserId() == id) {
//                user.setPassword(newPlainPassword);
//                updatedLines.add(userToLine(user));
//                updated = true;
//            } else {
//                updatedLines.add(line);
//            }
//        }
//
//        return updated && FileManager.writeAllLines(USERS_FILE, updatedLines);
//    }
//
//    /**
//     * Deletes a user from the file.
//     *
//     * @param userId The user's ID as a string.
//     * @return true if the deletion is successful; false otherwise.
//     */
//    public boolean deleteUser(String userId) {
//        int id;
//        try {
//            id = Integer.parseInt(userId);
//        } catch (NumberFormatException e) {
//            logger.log(Level.WARNING, "Invalid user ID format: " + userId);
//            return false;
//        }
//
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//        List<String> updatedLines = new ArrayList<>();
//        boolean deleted = false;
//
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null && user.getUserId() == id) {
//                deleted = true;
//            } else {
//                updatedLines.add(line);
//            }
//        }
//
//        return deleted && FileManager.writeAllLines(USERS_FILE, updatedLines);
//    }
//
//    /**
//     * Retrieves a list of all users who have the role of "vehicle_owner" from the file.
//     * @return a list of `User` objects representing vehicle owners.
//     */
//    public List<User> getAllVehicleOwners() {
//        List<User> owners = new ArrayList<>();
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null && user.hasRole("vehicle_owner")) {
//                owners.add(user);
//            }
//        }
//
//        return owners;
//    }
//
//    /**
//     * Authenticates a user by email and plain-text password.
//     * Uses PasswordUtil to check the password against the stored hash.
//     *
//     * @param email The user's email.
//     * @param plainPassword The plain-text password.
//     * @return A User object if authentication is successful; null otherwise.
//     */
//
//    public User authenticate(String email, String plainPassword) {
//        List<String> lines = FileManager.readAllLines(USERS_FILE);
//
//        for (String line : lines) {
//            User user = lineToUser(line);
//            if (user != null && email.equals(user.getEmail())) {
//                String storedPassword = user.getPassword();
//                if (plainPassword.equals(storedPassword)) {
//                    logger.info("User authenticated: " + user.getUserId());
//                    return user;
//                } else {
//                    logger.warning("Authentication failed for email: " + email);
//                }
//                break; // Exit once we've found the user with this email
//            }
//        }
//
//        return null;
//    }
//
//    // Review this method 
//    
//}


package dao;

import db.DatabaseManager;
import models.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UserDAO {
    private static final Logger logger = Logger.getLogger(UserDAO.class.getName());

    /**
     * Adds a new user to the database.
     */
    public boolean addUser(User user) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseManager.getConnection();
            String sql = "INSERT INTO users (full_name, email, roles, password) VALUES (?, ?, ?, ?)";
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getRolesAsString());
            stmt.setString(4, user.getPassword());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Get the generated ID
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    user.setUserId(rs.getInt(1));
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error adding user: " + e.getMessage(), e);
            return false;
        } finally {
            DatabaseManager.closeResources(stmt, conn);
        }
    }

    /**
     * Retrieves a user from the database by user ID.
     */
    public User getUserById(int userId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            String sql = "SELECT * FROM users WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                User user = new User(
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("roles"),
                    rs.getString("password")
                );
                user.setUserId(rs.getInt("user_id"));
                return user;
            }
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting user by ID: " + e.getMessage(), e);
            return null;
        } finally {
            DatabaseManager.closeResources(rs, stmt, conn);
        }
    }

    /**
     * Retrieves all users from the database.
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.createStatement();
            String sql = "SELECT * FROM users";
            
            rs = stmt.executeQuery(sql);
            
            while (rs.next()) {
                User user = new User(
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("roles"),
                    rs.getString("password")
                );
                user.setUserId(rs.getInt("user_id"));
                users.add(user);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting all users: " + e.getMessage(), e);
        } finally {
            DatabaseManager.closeResources(rs, stmt, conn);
        }
        
        return users;
    }

    /**
     * Updates an existing user's details (except the password).
     */
    public boolean updateUser(User user) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            conn = DatabaseManager.getConnection();
            String sql = "UPDATE users SET full_name = ?, email = ?, roles = ? WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getRolesAsString());
            stmt.setInt(4, user.getUserId());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating user: " + e.getMessage(), e);
            return false;
        } finally {
            DatabaseManager.closeResources(stmt, conn);
        }
    }

    /**
     * Updates a user's password.
     */
    public boolean updatePassword(String userId, String newPlainPassword) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int id = Integer.parseInt(userId);
            
            conn = DatabaseManager.getConnection();
            String sql = "UPDATE users SET password = ? WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            
            stmt.setString(1, newPlainPassword);
            stmt.setInt(2, id);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid user ID format: " + userId);
            return false;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating password: " + e.getMessage(), e);
            return false;
        } finally {
            DatabaseManager.closeResources(stmt, conn);
        }
    }

    /**
     * Deletes a user from the database.
     */
    public boolean deleteUser(String userId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        
        try {
            int id = Integer.parseInt(userId);
            
            conn = DatabaseManager.getConnection();
            String sql = "DELETE FROM users WHERE user_id = ?";
            stmt = conn.prepareStatement(sql);
            
            stmt.setInt(1, id);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid user ID format: " + userId);
            return false;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting user: " + e.getMessage(), e);
            return false;
        } finally {
            DatabaseManager.closeResources(stmt, conn);
        }
    }

    /**
     * Retrieves a list of all users who have the role of "vehicle_owner".
     */
    public List<User> getAllVehicleOwners() {
        List<User> owners = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            String sql = "SELECT * FROM users WHERE roles LIKE ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, "%vehicle_owner%");
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                User user = new User(
                    rs.getString("full_name"),
                    rs.getString("email"),
                    rs.getString("roles"),
                    rs.getString("password")
                );
                user.setUserId(rs.getInt("user_id"));
                owners.add(user);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting vehicle owners: " + e.getMessage(), e);
        } finally {
            DatabaseManager.closeResources(rs, stmt, conn);
        }
        
        return owners;
    }

    /**
     * Authenticates a user by email and plain-text password.
     */
    public User authenticate(String email, String plainPassword) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            String sql = "SELECT * FROM users WHERE email = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);
            
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                String storedPassword = rs.getString("password");
                
                if (plainPassword.equals(storedPassword)) {
                    User user = new User(
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("roles"),
                        rs.getString("password")
                    );
                    user.setUserId(rs.getInt("user_id"));
                    logger.info("User authenticated: " + user.getUserId());
                    return user;
                } else {
                    logger.warning("Authentication failed for email: " + email);
                }
            }
            return null;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error authenticating user: " + e.getMessage(), e);
            return null;
        } finally {
            DatabaseManager.closeResources(rs, stmt, conn);
        }
    }
}





