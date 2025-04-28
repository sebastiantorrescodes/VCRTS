package models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Vehicle {
    private String ownerId;           // Changed to String to be a free-form field
    private int vehicleOwnerId;       // References actual user in database
    private String model;
    private String make;
    private String year;
    private String vin;
    private String residencyTime;
    private String registeredTimestamp;

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Constructor with the free-form ownerId and vehicleOwnerId
    public Vehicle(String ownerId, int vehicleOwnerId, String model, String make, String year, String vin, String residencyTime) {
        this.ownerId = ownerId;
        this.vehicleOwnerId = vehicleOwnerId;
        this.model = model;
        this.make = make;
        this.year = year;
        this.vin = vin;
        this.residencyTime = residencyTime;
        this.registeredTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    // Constructor with timestamp parameter
    public Vehicle(String ownerId, int vehicleOwnerId, String model, String make, String year, String vin, String residencyTime, String registeredTimestamp) {
        this.ownerId = ownerId;
        this.vehicleOwnerId = vehicleOwnerId;
        this.model = model;
        this.make = make;
        this.year = year;
        this.vin = vin;
        this.residencyTime = residencyTime;
        this.registeredTimestamp = registeredTimestamp;
    }

    // Backward compatibility constructor (converts int to String for ownerId)
    public Vehicle(int ownerId, String model, String make, String year, String vin, String residencyTime) {
        this.ownerId = String.valueOf(ownerId);
        this.vehicleOwnerId = ownerId; // For compatibility, use the same ID for both
        this.model = model;
        this.make = make;
        this.year = year;
        this.vin = vin;
        this.residencyTime = residencyTime;
        this.registeredTimestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }

    // Backward compatibility constructor with timestamp
    public Vehicle(int ownerId, String model, String make, String year, String vin, String residencyTime, String registeredTimestamp) {
        this.ownerId = String.valueOf(ownerId);
        this.vehicleOwnerId = ownerId; // For compatibility, use the same ID for both
        this.model = model;
        this.make = make;
        this.year = year;
        this.vin = vin;
        this.residencyTime = residencyTime;
        this.registeredTimestamp = registeredTimestamp;
    }

    // Getters and setters
    public String getOwnerId() { 
        return ownerId; 
    }
    
    public void setOwnerId(String ownerId) { 
        this.ownerId = ownerId; 
    }
    
    public int getVehicleOwnerId() { 
        return vehicleOwnerId; 
    }
    
    public void setVehicleOwnerId(int vehicleOwnerId) { 
        this.vehicleOwnerId = vehicleOwnerId; 
    }
    
    public String getModel() { 
        return model; 
    }
    
    public void setModel(String model) { 
        this.model = model; 
    }
    
    public String getMake() { 
        return make; 
    }
    
    public void setMake(String make) { 
        this.make = make; 
    }
    
    public String getYear() { 
        return year; 
    }
    
    public void setYear(String year) { 
        this.year = year; 
    }
    
    public String getVin() { 
        return vin; 
    }
    
    public void setVin(String vin) { 
        this.vin = vin; 
    }
    
    public String getResidencyTime() { 
        return residencyTime; 
    }
    
    public void setResidencyTime(String residencyTime) { 
        this.residencyTime = residencyTime; 
    }
    
    public String getRegisteredTimestamp() { 
        return registeredTimestamp; 
    }
    
    public void setRegisteredTimestamp(String registeredTimestamp) { 
        this.registeredTimestamp = registeredTimestamp; 
    }

    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(TIMESTAMP_FORMATTER);
    }
    
    @Override
    public String toString() {
        return "Vehicle [ownerId=" + ownerId + ", vehicleOwnerId=" + vehicleOwnerId + ", model=" + model + 
               ", make=" + make + ", year=" + year + ", vin=" + vin + ", residencyTime=" + residencyTime + 
               ", registeredTimestamp=" + registeredTimestamp + "]";
    }
}