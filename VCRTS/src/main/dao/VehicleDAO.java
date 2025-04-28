package dao;

import db.DatabaseManager;
import models.Vehicle;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VehicleDAO {
    private static final Logger logger = Logger.getLogger(VehicleDAO.class.getName());
    
    // Updated SQL queries
    private static final String SELECT_ALL_VEHICLES = "SELECT * FROM vehicles";
    private static final String SELECT_VEHICLES_BY_OWNER_STRING = "SELECT * FROM vehicles WHERE owner_id = ?";
    private static final String SELECT_VEHICLES_BY_VEHICLE_OWNER = "SELECT * FROM vehicles WHERE vehicle_owner_id = ?";
    private static final String INSERT_VEHICLE = "INSERT INTO vehicles (owner_id, vehicle_owner_id, model, make, year, vin, residency_time, registered_timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_VEHICLE = "DELETE FROM vehicles WHERE vin = ?";
    private static final String UPDATE_VEHICLE = "UPDATE vehicles SET owner_id = ?, vehicle_owner_id = ?, model = ?, make = ?, year = ?, residency_time = ? WHERE vin = ?";
    private static final String SELECT_VEHICLE_BY_VIN = "SELECT * FROM vehicles WHERE vin = ?";
    private static final String CHECK_OWNER_EXISTS = "SELECT COUNT(*) FROM users WHERE user_id = ?";

    /**
     * Creates a Vehicle object from a ResultSet row.
     */
    private Vehicle resultSetToVehicle(ResultSet rs) throws SQLException {
        return new Vehicle(
            rs.getString("owner_id"),
            rs.getInt("vehicle_owner_id"),
            rs.getString("model"),
            rs.getString("make"),
            rs.getString("year"),
            rs.getString("vin"),
            rs.getString("residency_time"),
            rs.getString("registered_timestamp")
        );
    }

    /**
     * Retrieves all vehicles from the database.
     * @return A list of all vehicles.
     */
    public List<Vehicle> getAllVehicles() {
        List<Vehicle> vehicles = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(SELECT_ALL_VEHICLES);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                vehicles.add(resultSetToVehicle(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving all vehicles", e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return vehicles;
    }

    /**
     * Retrieves a list of vehicles with the specified owner ID string.
     *
     * @param ownerId The owner ID string of the vehicles.
     * @return A list of vehicles with the specified owner ID.
     */
    public List<Vehicle> getVehiclesByOwnerString(String ownerId) {
        List<Vehicle> vehicles = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(SELECT_VEHICLES_BY_OWNER_STRING);
            stmt.setString(1, ownerId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                vehicles.add(resultSetToVehicle(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving vehicles for owner string: " + ownerId, e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return vehicles;
    }
    
    /**
     * Retrieves a list of vehicles owned by a specific user (using vehicle_owner_id).
     *
     * @param vehicleOwnerId The user ID of the vehicle owner.
     * @return A list of vehicles belonging to the specified owner.
     */
    public List<Vehicle> getVehiclesByVehicleOwner(int vehicleOwnerId) {
        List<Vehicle> vehicles = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(SELECT_VEHICLES_BY_VEHICLE_OWNER);
            stmt.setInt(1, vehicleOwnerId);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                vehicles.add(resultSetToVehicle(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving vehicles for vehicle owner ID: " + vehicleOwnerId, e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return vehicles;
    }
    
    /**
     * Backward compatibility method - Retrieves vehicles by owner ID (as int).
     * This now uses the vehicle_owner_id column.
     *
     * @param ownerId The ID of the vehicle owner.
     * @return A list of vehicles belonging to the specified owner.
     */
    public List<Vehicle> getVehiclesByOwner(int ownerId) {
        return getVehiclesByVehicleOwner(ownerId);
    }

    /**
     * Checks if a user with the specified ID exists.
     * 
     * @param userId The user ID to check
     * @return true if the user exists, false otherwise
     */
    public boolean checkOwnerExists(int userId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        boolean exists = false;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(CHECK_OWNER_EXISTS);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if user exists: " + userId, e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return exists;
    }

    /**
     * Adds a new vehicle record to the database.
     *
     * @param vehicle The Vehicle object to be added.
     * @return true if the vehicle was successfully added, false otherwise.
     */
    public boolean addVehicle(Vehicle vehicle) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(INSERT_VEHICLE);
            stmt.setString(1, vehicle.getOwnerId());
            stmt.setInt(2, vehicle.getVehicleOwnerId());
            stmt.setString(3, vehicle.getModel());
            stmt.setString(4, vehicle.getMake());
            stmt.setString(5, vehicle.getYear());
            stmt.setString(6, vehicle.getVin());
            stmt.setString(7, vehicle.getResidencyTime());
            stmt.setString(8, vehicle.getRegisteredTimestamp());
            
            int rowsAffected = stmt.executeUpdate();
            success = rowsAffected > 0;
        } catch (SQLException e) {
            if (e.getMessage().contains("foreign key constraint") || e.getErrorCode() == 1452) {
                logger.log(Level.SEVERE, "Foreign key constraint error adding vehicle: " + vehicle.getVin() 
                    + ". Vehicle Owner ID " + vehicle.getVehicleOwnerId() + " might not exist.", e);
            } else {
                logger.log(Level.SEVERE, "Error adding vehicle: " + vehicle.getVin(), e);
            }
        } finally {
            DatabaseManager.closeResources(stmt);
        }
        
        return success;
    }

    /**
     * Deletes a vehicle from the database based on its VIN.
     *
     * @param vin The VIN of the vehicle to be deleted.
     * @return true if the vehicle was successfully deleted, false otherwise.
     */
    public boolean deleteVehicle(String vin) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(DELETE_VEHICLE);
            stmt.setString(1, vin);
            
            int rowsAffected = stmt.executeUpdate();
            success = rowsAffected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting vehicle with VIN: " + vin, e);
        } finally {
            DatabaseManager.closeResources(stmt);
        }
        
        return success;
    }

    /**
     * Updates an existing vehicle's details.
     * @param vehicle A Vehicle object with updated information.
     * @return true if the update is successful; false otherwise.
     */
    public boolean updateVehicle(Vehicle vehicle) {
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement selectStmt = null;
        ResultSet rs = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            
            // First, get the current registered timestamp to preserve it
            selectStmt = conn.prepareStatement(SELECT_VEHICLE_BY_VIN);
            selectStmt.setString(1, vehicle.getVin());
            rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                String originalTimestamp = rs.getString("registered_timestamp");
                
                // Now update the vehicle
                stmt = conn.prepareStatement(UPDATE_VEHICLE);
                stmt.setString(1, vehicle.getOwnerId());
                stmt.setInt(2, vehicle.getVehicleOwnerId());
                stmt.setString(3, vehicle.getModel());
                stmt.setString(4, vehicle.getMake());
                stmt.setString(5, vehicle.getYear());
                stmt.setString(6, vehicle.getResidencyTime());
                stmt.setString(7, vehicle.getVin());
                
                int rowsAffected = stmt.executeUpdate();
                success = rowsAffected > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating vehicle: " + vehicle.getVin(), e);
        } finally {
            DatabaseManager.closeResources(rs, selectStmt, stmt);
        }
        
        return success;
    }
}