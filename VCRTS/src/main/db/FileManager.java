package db;

import java.util.*; 
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.*;
import java.nio.file.*;

//This class provides methods to read from and write to text files 

public class FileManager {
    private static final Logger logger = Logger.getLogger(FileManager.class.getName());
    
    // Get the application's base directory
    private static final String BASE_DIR = determineBaseDirectory();
    private static final String DATA_DIR = Paths.get(BASE_DIR, "data").toString();
    
    static {
        try {
            // Create data directory if it doesn't exist 
            Files.createDirectories(Paths.get(DATA_DIR));
            logger.info("Using data directory: " + DATA_DIR);
            
            // Verify the data directory by checking for expected files or creating them
            verifyDataDirectory();
        }
        catch (IOException e){
            logger.log(Level.SEVERE, "Failed to create or access data directory: " + DATA_DIR, e);
        }
    }
    
    /**
     * Determines the base directory for the application.
     * First tries to find the directory where the application JAR is located.
     * Falls back to the current working directory if that fails.
     * 
     * @return The base directory path as a String
     */
    private static String determineBaseDirectory() {
        try {
            // Try to get the directory where the application is running from
            String jarPath = FileManager.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File jarFile = new File(jarPath);
            
            // If it's a JAR file, use its parent directory
            if (jarFile.isFile()) {
                return jarFile.getParent();
            }
            
            // Otherwise, check if we're running from a classes directory (dev environment)
            File currentDir = new File(".");
            if (new File(currentDir, "data").exists() || new File(currentDir, "VCRTS - Copy").exists()) {
                return currentDir.getAbsolutePath();
            }
            
            // If we can navigate up to find the data directory, do so
            File parentDir = currentDir.getParentFile();
            if (parentDir != null && (new File(parentDir, "data").exists() || 
                                      new File(parentDir, "VCRTS - Copy").exists())) {
                return parentDir.getAbsolutePath();
            }
            
            // As a fallback, use the current directory
            return currentDir.getAbsolutePath();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not determine application directory, using current directory", e);
            return System.getProperty("user.dir");
        }
    }
    
    /**
     * Verifies that the data directory contains the expected files, or creates them if missing.
     */
    private static void verifyDataDirectory() throws IOException {
        // List of essential data files that should exist
        String[] essentialFiles = {
            "users.txt", "jobs.txt", "vehicles.txt", "allocations.txt", 
            "job_schedule.txt", "job_states.txt"
        };
        
        // Check each file and create it if it doesn't exist
        for (String filename : essentialFiles) {
            Path filePath = Paths.get(DATA_DIR, filename);
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
                logger.info("Created missing data file: " + filename);
            }
        }
    }

    /**
     * Reads all lines from a file.
     * @param fileName The name of the file.
     * @return A list of strings, where each string is a line in the file.
     */
    public static List<String> readAllLines(String fileName){
        Path filePath = Paths.get(DATA_DIR, fileName);
        try {
            if (!Files.exists(filePath)){
                Files.createFile(filePath);
                logger.info("Created new file during read attempt: " + fileName);
                return new ArrayList<>();
            }
            return Files.readAllLines(filePath);
        } catch (IOException e){
            logger.log(Level.SEVERE, "Error reading file: " + fileName + " from path: " + filePath, e);
            return new ArrayList<>();
        }
    }

    /**
     * Writes all lines to a file.
     * @param fileName The name of the file.
     * @param lines The lines to write.
     * @return true if the operation was successful, false otherwise.
     */

    public static boolean writeAllLines(String fileName, List<String> lines){
        Path filePath = Paths.get(DATA_DIR, fileName);
        try {
            Files.write(filePath, lines);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error writing to file: " + fileName + " at path: " + filePath, e);
            return false;
        }
    }

    /**
     * Appends a single line to a file.
     * @param fileName The name of the file.
     * @param line The line to append.
     * @return true if the operation was successful, false otherwise.
     */

    public static boolean appendLine(String fileName, String line) {
        Path filePath = Paths.get(DATA_DIR, fileName);
        try {
            if (!Files.exists(filePath)) {
                Files.createFile(filePath);
                logger.info("Created new file during append attempt: " + fileName);
            }
            Files.write(filePath, (line + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error appending to file: " + fileName + " at path: " + filePath, e);
            return false;
        }
    }

    /**
     * Generates a unique ID.
     * @param fileName The file to check for existing IDs.
     * @param idPrefix A prefix for the ID (optional).
     * @return A unique ID string.
     */
    public static String generateUniqueId(String fileName, String idPrefix) {
        return idPrefix + System.currentTimeMillis();
    }

    /**
     * Generates a unique numeric ID.
     * @param fileName The file to check for existing IDs.
     * @return A unique numeric ID.
     */

    public static int generateUniqueNumericId(String fileName) {
        List<String> lines = readAllLines(fileName);
        int maxId = 0;

        for (String line : lines) {
            try {
                String[] parts = line.split("\\|");
                if (parts.length > 0) {
                    int id = Integer.parseInt(parts[0]);
                    if (id > maxId) {
                        maxId = id;
                    }
                }
            } catch (NumberFormatException e) {
                // Skip lines that don't start with a number
            }
        }

        return maxId + 1;
    }
    
    /**
     * Returns the current data directory path.
     * @return The path to the data directory.
     */
    public static String getDataDirectory() {
        return DATA_DIR;
    }
}