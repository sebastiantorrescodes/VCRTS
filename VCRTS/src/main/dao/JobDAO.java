//package dao;
//
//import java.util.*;
//import java.util.logging.*;
//import models.Job; 
//import db.FileManager; 
//
//public class JobDAO {
//    private static final Logger logger = Logger.getLogger(JobDAO.class.getName());
//    private static final String JOBS_FILE= "jobs.txt";
//    private static final String DELIMITER = "\\|";
//    private static final String SEPARATOR = "|";
//
//    private String jobToLine(Job job){
//        return job.getJobId() + SEPARATOR +
//                job.getJobName() + SEPARATOR +
//                job.getJobOwnerId() + SEPARATOR +
//                job.getDuration() + SEPARATOR +
//                job.getDeadline() + SEPARATOR +
//                job.getStatus() + SEPARATOR +
//                job.getCreatedTimestamp();
//    }
//
//    private Job lineToJob(String line) {
//        String[] parts = line.split(DELIMITER);
//        if (parts.length < 6) {
//            logger.warning("Invalid job data format: " + line);
//            return null;
//        }
//
//        try {
//            // Check if the timestamp is included in the line
//            String timestamp = parts.length >= 7 ? parts[6] : Job.getCurrentTimestamp();
//
//            return new Job(
//                    parts[0],                       // jobId
//                    parts[1],                       // jobName
//                    Integer.parseInt(parts[2]),     // jobOwnerId
//                    parts[3],                       // duration
//                    parts[4],                       // deadline
//                    parts[5],                       // status
//                    timestamp                       // createdTimestamp
//            );
//        } catch (NumberFormatException e) {
//            logger.log(Level.WARNING, "Error parsing job owner ID: " + parts[2], e);
//            return null;
//        }
//    }
//
//    /**
//     * Retrieves all jobs - SHOULD ONLY BE CALLED BY CLOUD CONTROLLER
//     * @return a list of {@code Job} objects representing all jobs in the file.
//     */
//
//    public List<Job> getAllJobs() {
//        List<Job> jobs = new ArrayList<>();
//        List<String> lines = FileManager.readAllLines(JOBS_FILE);
//
//        for (String line : lines) {
//            Job job = lineToJob(line);
//            if (job != null) {
//                jobs.add(job);
//            }
//        }
//
//        return jobs;
//    }
//
//    /**
//     * Adds a new job to the file.
//     * @param job the {Job} object containing job details.
//     * @return true if the job was successfully added, otherwise false
//     */
//    public boolean addJob(Job job) {
//        // Jobs already have IDs set by the application
//        String jobLine = jobToLine(job);
//        return FileManager.appendLine(JOBS_FILE, jobLine);
//    }
//
//    /**
//     * Deletes a job from the file.
//     * @param jobId the unique identifier of the job to be deleted.
//     * @return true if the job was successfully deleted, otherwise false
//     */
//
//    public boolean deleteJob(String jobId) {
//        List<String> lines = FileManager.readAllLines(JOBS_FILE);
//        List<String> updatedLines = new ArrayList<>();
//        boolean deleted = false;
//
//        for (String line : lines) {
//            Job job = lineToJob(line);
//            if (job != null && jobId.equals(job.getJobId())) {
//                deleted = true;
//            } else {
//                updatedLines.add(line);
//            }
//        }
//
//        return deleted && FileManager.writeAllLines(JOBS_FILE, updatedLines);
//    }
//
//    /**
//     * Retrieves jobs for a given client (job owner) filtered by status.
//     * If status equals "All" (case-insensitive), all jobs for the client are returned.
//     * This method ensures clients can only see their own jobs.
//     *
//     * @param clientId The job owner's ID.
//     * @param status The status filter ("All", "Queued", "In Progress", "Completed", etc.).
//     * @return A list of Job objects
//     */
//
//    public List<Job> getJobsByClient(int clientId, String status) {
//        List<Job> jobs = new ArrayList<>();
//        List<String> lines = FileManager.readAllLines(JOBS_FILE);
//
//        for (String line : lines) {
//            Job job = lineToJob(line);
//
//            // Only return jobs that belong to this client
//            if (job != null && job.getJobOwnerId() == clientId) {
//                if ("All".equalsIgnoreCase(status) || status.equalsIgnoreCase(job.getStatus())) {
//                    jobs.add(job);
//                }
//            }
//        }
//
//        return jobs;
//    }
//
//    /**
//     * Updates an existing job's details.
//     * @param job A Job object with updated information.
//     * @return true if the update is successful; false otherwise.
//     */
//        public boolean updateJob(Job job) {
//        List<String> lines = FileManager.readAllLines(JOBS_FILE);
//        List<String> updatedLines = new ArrayList<>();
//        boolean updated = false;
//
//        for (String line : lines) {
//            Job existingJob = lineToJob(line);
//            if (existingJob != null && existingJob.getJobId().equals(job.getJobId())) {
//                // Preserve the original timestamp
//                job.setCreatedTimestamp(existingJob.getCreatedTimestamp());
//                updatedLines.add(jobToLine(job));
//                updated = true;
//            } else {
//                updatedLines.add(line);
//            }
//        }
//
//        return updated && FileManager.writeAllLines(JOBS_FILE, updatedLines);
//    }
//}

package dao;

import db.DatabaseManager;
import models.Job;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JobDAO {
    private static final Logger logger = Logger.getLogger(JobDAO.class.getName());
    
    // SQL queries
    private static final String SELECT_ALL_JOBS = "SELECT * FROM jobs";
    private static final String SELECT_JOBS_BY_CLIENT = "SELECT * FROM jobs WHERE job_owner_id = ?";
    private static final String SELECT_JOBS_BY_CLIENT_AND_STATUS = "SELECT * FROM jobs WHERE job_owner_id = ? AND status = ?";
    private static final String INSERT_JOB = "INSERT INTO jobs (job_id, job_name, job_owner_id, duration, deadline, status, created_timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_JOB = "DELETE FROM jobs WHERE job_id = ?";
    private static final String UPDATE_JOB = "UPDATE jobs SET job_name = ?, job_owner_id = ?, duration = ?, deadline = ?, status = ? WHERE job_id = ?";
    private static final String SELECT_JOB_BY_ID = "SELECT * FROM jobs WHERE job_id = ?";

    /**
     * Creates a Job object from a ResultSet row.
     */
    private Job resultSetToJob(ResultSet rs) throws SQLException {
        return new Job(
            rs.getString("job_id"),
            rs.getString("job_name"),
            rs.getInt("job_owner_id"),
            rs.getString("duration"),
            rs.getString("deadline"),
            rs.getString("status"),
            rs.getString("created_timestamp")
        );
    }

    /**
     * Retrieves all jobs - SHOULD ONLY BE CALLED BY CLOUD CONTROLLER
     * @return a list of {@code Job} objects representing all jobs in the database.
     */
    public List<Job> getAllJobs() {
        List<Job> jobs = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(SELECT_ALL_JOBS);
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                jobs.add(resultSetToJob(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving all jobs", e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return jobs;
    }

    /**
     * Adds a new job to the database.
     * @param job the {Job} object containing job details.
     * @return true if the job was successfully added, otherwise false
     */
    public boolean addJob(Job job) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(INSERT_JOB);
            stmt.setString(1, job.getJobId());
            stmt.setString(2, job.getJobName());
            stmt.setInt(3, job.getJobOwnerId());
            stmt.setString(4, job.getDuration());
            stmt.setString(5, job.getDeadline());
            stmt.setString(6, job.getStatus());
            stmt.setString(7, job.getCreatedTimestamp());
            
            int rowsAffected = stmt.executeUpdate();
            success = rowsAffected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error adding job: " + job.getJobId(), e);
        } finally {
            DatabaseManager.closeResources(stmt);
        }
        
        return success;
    }

    /**
     * Deletes a job from the database.
     * @param jobId the unique identifier of the job to be deleted.
     * @return true if the job was successfully deleted, otherwise false
     */
    public boolean deleteJob(String jobId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            stmt = conn.prepareStatement(DELETE_JOB);
            stmt.setString(1, jobId);
            
            int rowsAffected = stmt.executeUpdate();
            success = rowsAffected > 0;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting job with ID: " + jobId, e);
        } finally {
            DatabaseManager.closeResources(stmt);
        }
        
        return success;
    }

    /**
     * Retrieves jobs for a given client (job owner) filtered by status.
     * If status equals "All" (case-insensitive), all jobs for the client are returned.
     * This method ensures clients can only see their own jobs.
     *
     * @param clientId The job owner's ID.
     * @param status The status filter ("All", "Queued", "In Progress", "Completed", etc.).
     * @return A list of Job objects
     */
    public List<Job> getJobsByClient(int clientId, String status) {
        List<Job> jobs = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseManager.getConnection();
            
            if ("All".equalsIgnoreCase(status)) {
                stmt = conn.prepareStatement(SELECT_JOBS_BY_CLIENT);
                stmt.setInt(1, clientId);
            } else {
                stmt = conn.prepareStatement(SELECT_JOBS_BY_CLIENT_AND_STATUS);
                stmt.setInt(1, clientId);
                stmt.setString(2, status);
            }
            
            rs = stmt.executeQuery();
            
            while (rs.next()) {
                jobs.add(resultSetToJob(rs));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving jobs for client: " + clientId + " with status: " + status, e);
        } finally {
            DatabaseManager.closeResources(rs, stmt);
        }
        
        return jobs;
    }

    /**
     * Updates an existing job's details.
     * @param job A Job object with updated information.
     * @return true if the update is successful; false otherwise.
     */
    public boolean updateJob(Job job) {
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement selectStmt = null;
        ResultSet rs = null;
        boolean success = false;
        
        try {
            conn = DatabaseManager.getConnection();
            
            // First, get the current created timestamp to preserve it
            selectStmt = conn.prepareStatement(SELECT_JOB_BY_ID);
            selectStmt.setString(1, job.getJobId());
            rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                String originalTimestamp = rs.getString("created_timestamp");
                
                // Now update the job
                stmt = conn.prepareStatement(UPDATE_JOB);
                stmt.setString(1, job.getJobName());
                stmt.setInt(2, job.getJobOwnerId());
                stmt.setString(3, job.getDuration());
                stmt.setString(4, job.getDeadline());
                stmt.setString(5, job.getStatus());
                stmt.setString(6, job.getJobId());
                
                int rowsAffected = stmt.executeUpdate();
                success = rowsAffected > 0;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating job: " + job.getJobId(), e);
        } finally {
            DatabaseManager.closeResources(rs, selectStmt, stmt);
        }
        
        return success;
    }
}
