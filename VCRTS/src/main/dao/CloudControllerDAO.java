package dao;

import db.FileManager;
import models.Job;
import models.PendingRequest;
import models.User;
import models.Vehicle;

import javax.swing.SwingUtilities; // Import SwingUtilities
import javax.swing.JOptionPane;   // Import JOptionPane for showing errors from background thread

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;

/**
 * Data Access Object for Cloud Controller operations.
 * Implements job scheduling, completion time calculation, and approval workflow.
 */
public class CloudControllerDAO {
    private static final Logger logger = Logger.getLogger(CloudControllerDAO.class.getName());
    private static final String SCHEDULE_FILE = "job_schedule.txt";
    private static final String JOB_STATE_FILE = "job_states.txt";
    private static final String DELIMITER = "\\|";
    private static final String SEPARATOR = "|";
    private static final String PENDING_REQUESTS_FILE = "pending_requests.txt";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Job states
    public static final String STATE_QUEUED = "Queued";
    public static final String STATE_PROGRESS = "In Progress";
    public static final String STATE_COMPLETED = "Completed";
    public static final String STATE_PENDING_APPROVAL = "Pending Approval"; // New state

    private JobDAO jobDAO;
    private VehicleDAO vehicleDAO;

    // Use a standard ArrayList, but access MUST BE synchronized
    private static final List<PendingRequest> pendingRequests = new ArrayList<>();

    // HashMap to store job duration (in minutes) for each job ID
    private Map<String, Long> jobDurations = new HashMap<>();

    public CloudControllerDAO() {
        this.jobDAO = new JobDAO();
        this.vehicleDAO = new VehicleDAO();
        loadPendingRequests();
    }

    // --- Approval Workflow Methods (Modified for synchronized List) ---

    /**
     * Adds a new job request to the pending queue for approval.
     * Does NOT save the job to jobs.txt yet.
     * @param job The job submitted.
     * @param submittedBy The user who submitted the job.
     * @return true if the request was added to the queue.
     */
public boolean submitJobForApproval(Job job, User submittedBy) {
    job.setStatus(STATE_PENDING_APPROVAL);
    PendingRequest request = new PendingRequest(PendingRequest.RequestType.JOB, job, submittedBy);
    boolean added;
    synchronized (pendingRequests) {
        added = pendingRequests.add(request);
    }
    if (added) {
        logger.info("Job submitted for approval: " + job.getJobId() + " by " + submittedBy.getFullName());
        savePendingRequests(); // Save after modifying the list
    } else {
        logger.warning("Failed to add job request to pending queue: " + job.getJobId());
    }
    return added;
}

/**
 * Saves the current pending requests to disk.
 */

/**
 * Saves the current pending requests to disk.
 */
private void savePendingRequests() {
    synchronized (pendingRequests) {
        List<String> lines = new ArrayList<>();
        for (PendingRequest req : pendingRequests) {
            // Format: requestType|submittedByInfo|dataDetails
            String line;
            if (req.getType() == PendingRequest.RequestType.JOB) {
                Job job = (Job) req.getData();
                line = "JOB|" + req.getSubmittedByInfo() + "|" + job.getJobId() + "|" + 
                       job.getJobName() + "|" + job.getJobOwnerId() + "|" + 
                       job.getDuration() + "|" + job.getDeadline() + "|" + 
                       job.getStatus() + "|" + job.getCreatedTimestamp();
            } else {
                Vehicle vehicle = (Vehicle) req.getData();
                line = "VEHICLE|" + req.getSubmittedByInfo() + "|" + vehicle.getOwnerId() + "|" +
                       vehicle.getModel() + "|" + vehicle.getMake() + "|" + 
                       vehicle.getYear() + "|" + vehicle.getVin() + "|" + 
                       vehicle.getResidencyTime() + "|" + vehicle.getRegisteredTimestamp();
            }
            lines.add(line);
        }
        FileManager.writeAllLines(PENDING_REQUESTS_FILE, lines);
    }
}

/**
 * Loads pending requests from disk.
 */
private void loadPendingRequests() {
    synchronized (pendingRequests) {
        pendingRequests.clear();
        List<String> lines = FileManager.readAllLines(PENDING_REQUESTS_FILE);
        
        for (String line : lines) {
            String[] parts = line.split("\\|");
            if (parts.length < 3) continue;
            
            String typeStr = parts[0];
            String submittedByInfo = parts[1];
            
            if ("JOB".equals(typeStr) && parts.length >= 9) {
                String jobId = parts[2];
                String jobName = parts[3];
                int jobOwnerId = Integer.parseInt(parts[4]);
                String duration = parts[5];
                String deadline = parts[6];
                String status = parts[7];
                String timestamp = parts[8];
                
                Job job = new Job(jobId, jobName, jobOwnerId, duration, deadline, status, timestamp);
                PendingRequest req = new PendingRequest(PendingRequest.RequestType.JOB, job, null);
                // Use reflection to set the submitted info directly
                try {
                    java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("submittedByInfo");
                    field.setAccessible(true);
                    field.set(req, submittedByInfo);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error setting submittedByInfo", e);
                }
                pendingRequests.add(req);
            } 
            else if ("VEHICLE".equals(typeStr) && parts.length >= 9) {
                int ownerId = Integer.parseInt(parts[2]);
                String model = parts[3];
                String make = parts[4];
                String year = parts[5];
                String vin = parts[6];
                String residencyTime = parts[7];
                String timestamp = parts[8];
                
                Vehicle vehicle = new Vehicle(ownerId, model, make, year, vin, residencyTime, timestamp);
                PendingRequest req = new PendingRequest(PendingRequest.RequestType.VEHICLE, vehicle, null);
                // Use reflection to set the submitted info directly
                try {
                    java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("submittedByInfo");
                    field.setAccessible(true);
                    field.set(req, submittedByInfo);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error setting submittedByInfo", e);
                }
                pendingRequests.add(req);
            }
        }
    }
}

    /**
     * Adds a new vehicle registration request to the pending queue for approval.
     * Does NOT save the vehicle to vehicles.txt yet.
     * @param vehicle The vehicle submitted.
     * @param submittedBy The user who submitted the vehicle.
     * @return true if the request was added to the queue.
     */
public boolean submitVehicleForApproval(Vehicle vehicle, User submittedBy) {
    PendingRequest request = new PendingRequest(PendingRequest.RequestType.VEHICLE, vehicle, submittedBy);
    boolean added;
    synchronized (pendingRequests) {
        added = pendingRequests.add(request);
    }
    if (added) {
        logger.info("Vehicle submitted for approval: " + vehicle.getVin() + " by " + submittedBy.getFullName());
        savePendingRequests(); // Save after modifying the list
    } else {
        logger.warning("Failed to add vehicle request to pending queue: " + vehicle.getVin());
    }
    return added;
}

    /**
     * Retrieves all current pending requests.
     * @return A list of PendingRequest objects (thread-safe copy).
     */
public List<PendingRequest> getPendingRequests() {
    loadPendingRequests(); // Refresh from disk before returning
    synchronized (pendingRequests) {
        return new ArrayList<>(pendingRequests);
    }
}

    /**
     * Processes the approval of a pending request. Finds the request by ID,
     * removes it from the queue, and starts a background thread to save the data.
     * Note: This method now returns void as success/failure is handled by the background thread.
     * @param requestId The ID of the request to approve.
     * @param callback A Runnable to execute on the EDT after processing (e.g., refresh UI)
     */
    public void approveRequest(int requestId, Runnable callback) {
        PendingRequest requestToProcess = null;
        boolean removed = false;

        // 1. Find and remove the request SYNCHRONOUSLY (and safely)
        synchronized (pendingRequests) {
            Iterator<PendingRequest> iterator = pendingRequests.iterator();
            while (iterator.hasNext()) {
                PendingRequest req = iterator.next();
                if (req.getRequestId() == requestId) {
                    requestToProcess = req;
                    iterator.remove(); // Safely remove while iterating
                    removed = true;
                    break;
                }
            }
        }

        // 2. If found and removed, start a background thread to save
        if (requestToProcess != null && removed) {
            savePendingRequests();
            final PendingRequest finalRequest = requestToProcess; // Need final variable for lambda
            logger.info("Request " + requestId + " removed from queue. Starting background save thread.");

            // *** EXPLICIT THREAD CREATION ***
            Thread saveThread = new Thread(() -> {
                boolean saved = false;
                String message;
                boolean isError = false;

                try {
                    // Perform the file saving in the background thread
                    if (finalRequest.getType() == PendingRequest.RequestType.JOB) {
                        Job job = (Job) finalRequest.getData();
                        job.setStatus(STATE_QUEUED); // Set status before saving
                        saved = jobDAO.addJob(job);
                        if (saved) {
                            logger.info("Background thread successfully saved Job ID: " + job.getJobId());
                            message = "Request ID " + requestId + " (Job: " + job.getJobId() + ") approved and saved.";
                        } else {
                            logger.severe("Background thread FAILED to save approved Job ID: " + job.getJobId());
                            message = "Error saving approved Job ID: " + job.getJobId() + ". Please check logs.";
                            isError = true;
                            // Consider how to handle save failure - maybe re-add request?
                        }
                    } else if (finalRequest.getType() == PendingRequest.RequestType.VEHICLE) {
                        Vehicle vehicle = (Vehicle) finalRequest.getData();
                        saved = vehicleDAO.addVehicle(vehicle);
                        if (saved) {
                            logger.info("Background thread successfully saved Vehicle VIN: " + vehicle.getVin());
                             message = "Request ID " + requestId + " (Vehicle: " + vehicle.getVin() + ") approved and saved.";
                        } else {
                            logger.severe("Background thread FAILED to save approved Vehicle VIN: " + vehicle.getVin());
                             message = "Error saving approved Vehicle VIN: " + vehicle.getVin() + ". Please check logs.";
                             isError = true;
                            // Consider how to handle save failure
                        }
                    } else {
                         message = "Unknown request type for ID " + requestId;
                         isError = true;
                    }

                    // 3. Schedule GUI updates back on the EDT
                    final String finalMessage = message;
                    final boolean finalIsError = isError;
                    final boolean finalSaved = saved; // Need final variable for lambda
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, // Use null parent for simplicity from background thread
                                finalMessage,
                                finalIsError ? "Approval Error" : "Approval Success",
                                finalIsError ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                        if (finalSaved) {
                             // Optionally trigger schedule recalculation only if a job was saved
                             if (finalRequest.getType() == PendingRequest.RequestType.JOB) {
                                 calculateCompletionTimes(); // Recalculate on EDT after save success
                             }
                        }
                        if (callback != null) {
                            callback.run(); // Run the provided callback (e.g., refresh UI) on EDT
                        }
                    });

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Exception in background save thread for request " + requestId, e);
                    // Report error back to EDT
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                "An unexpected error occurred while saving request " + requestId + ":\n" + e.getMessage(),
                                "Background Save Error", JOptionPane.ERROR_MESSAGE);
                         if (callback != null) {
                            callback.run(); // Still run callback to refresh UI state
                        }
                    });
                }
            });
            saveThread.setName("SaveRequest-" + requestId);
            saveThread.start(); // Start the background thread

        } else {
            // Request not found or couldn't be removed
            logger.warning("Approve Request: Request ID " + requestId + " not found or couldn't be removed.");
             // Still run callback to potentially refresh UI state
            if (callback != null) {
                SwingUtilities.invokeLater(callback);
            }
             // Optionally show a message that the request wasn't found
             // SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, ...));
        }
    }

    /**
     * Processes the rejection of a pending request.
     * Finds the request by ID and removes it from the queue without saving.
     * @param requestId The ID of the request to reject.
     * @return true if the request was found and removed, false otherwise.
     */
public boolean rejectRequest(int requestId) {
    boolean removed = false;
    synchronized (pendingRequests) { // Synchronize access
         Iterator<PendingRequest> iterator = pendingRequests.iterator();
         while(iterator.hasNext()){
             PendingRequest req = iterator.next();
             if(req.getRequestId() == requestId){
                 iterator.remove(); // Use iterator's remove method for safety
                 removed = true;
                 logger.info("Rejected and removed request ID: " + requestId);
                 // Optionally log details of the rejected item
                 if (req.getType() == PendingRequest.RequestType.JOB) {
                     logger.info("Rejected Job details: ID=" + ((Job)req.getData()).getJobId());
                 } else if (req.getType() == PendingRequest.RequestType.VEHICLE) {
                      logger.info("Rejected Vehicle details: VIN=" + ((Vehicle)req.getData()).getVin());
                 }
                 break; // Found and removed, exit loop
             }
         }
    }

    // Save changes to disk whether the request was found or not
    savePendingRequests();

    if (!removed) {
        logger.warning("Reject Request: Request ID " + requestId + " not found in pending queue.");
    }
    return removed;
}


    // --- Existing Scheduling Methods ---

    /**
     * Calculates job completion times using FIFO scheduling. Considers only saved jobs.
     * @return A map of job IDs to their calculated completion times.
     */
    public Map<String, String> calculateCompletionTimes() {
        List<Job> allJobs = jobDAO.getAllJobs().stream()
                             .filter(j -> !STATE_PENDING_APPROVAL.equals(j.getStatus()))
                             .collect(Collectors.toList());
        allJobs.sort(Comparator.comparing(Job::getCreatedTimestamp));

        Map<String, String> completionTimes = new LinkedHashMap<>();
        Map<String, String> jobStates = loadJobStates();

        jobDurations.clear();
        LocalDateTime currentTime = LocalDateTime.now();
        long totalMinutes = 0;
        Job inProgressJob = null;

        for (Job job : allJobs) {
            if (STATE_PROGRESS.equals(jobStates.getOrDefault(job.getJobId(), job.getStatus()))) {
                inProgressJob = job;
                break;
            }
        }

        for (Job job : allJobs) {
            String currentState = jobStates.getOrDefault(job.getJobId(), job.getStatus());

            if (STATE_COMPLETED.equals(currentState)) {
                String existingCompletionTime = loadSchedule().get(job.getJobId()); // Check saved schedule
                 completionTimes.put(job.getJobId(), existingCompletionTime != null ? existingCompletionTime : "Completed");
                 jobStates.put(job.getJobId(), STATE_COMPLETED); // Ensure state map is consistent
                continue;
            }

            Duration jobDuration = parseJobDuration(job);
            long durationMinutes = jobDuration.toMinutes();
            jobDurations.put(job.getJobId(), durationMinutes);
            totalMinutes += durationMinutes;

            String newStatus = currentState; // Start with current state
            if (inProgressJob == null) {
                inProgressJob = job;
                newStatus = STATE_PROGRESS;
            } else if (!job.equals(inProgressJob)) {
                newStatus = STATE_QUEUED;
            } else { // It is the inProgressJob
                 newStatus = STATE_PROGRESS;
            }

             // Only update if status actually changed or needs confirmation
             if (!newStatus.equals(currentState)) {
                job.setStatus(newStatus);
                jobDAO.updateJob(job); // Update file
                jobStates.put(job.getJobId(), newStatus); // Update state map
            }

            LocalDateTime completionTime = currentTime.plus(jobDuration);
            String completionTimeStr = completionTime.format(TIMESTAMP_FORMATTER);
            completionTimes.put(job.getJobId(), completionTimeStr);
            currentTime = completionTime;
        }

        saveSchedule(completionTimes);
        saveJobStates(jobStates);
        return completionTimes;
    }

    /**
     * Gets job duration in a human-readable format.
     * @param jobId The job ID.
     * @return Human-readable duration string.
     */
     public String getJobDurationFormatted(String jobId) {
        Long minutes = jobDurations.get(jobId);
        if (minutes == null) {
             List<Job> jobs = jobDAO.getAllJobs();
             for (Job job : jobs) {
                 if (job.getJobId().equals(jobId)) {
                    Duration duration = parseJobDuration(job);
                    minutes = duration.toMinutes();
                    jobDurations.put(jobId, minutes);
                    break;
                 }
             }
             if (minutes == null) return "Unknown";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours > 0 && remainingMinutes > 0) {
             return String.format("%d hour%s %d min%s", hours, hours != 1 ? "s" : "", remainingMinutes, remainingMinutes != 1 ? "s" : "");
        } else if (hours > 0) {
             return String.format("%d hour%s", hours, hours != 1 ? "s" : "");
        } else {
             return String.format("%d min%s", remainingMinutes, remainingMinutes != 1 ? "s" : "");
        }
    }

    /**
     * Parse job duration from string format to Duration. Made public previously.
     * @param job The job to parse duration from.
     * @return Duration object representing the job's processing time.
     */
    public Duration parseJobDuration(Job job) {
        LocalTime durationTime;
        try {
            durationTime = LocalTime.parse(job.getDuration(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            logger.log(Level.WARNING, "Invalid duration format for job " + job.getJobId() + ": " + job.getDuration() + ". Defaulting to 1 hour.");
            durationTime = LocalTime.of(1, 0, 0);
        }
        return Duration.ofHours(durationTime.getHour())
                .plusMinutes(durationTime.getMinute())
                .plusSeconds(durationTime.getSecond());
    }

    /**
     * Marks the currently in-progress job as completed and advances the queue.
     * @return The ID of the newly in-progress job, or null if no jobs are available.
     */
    public String advanceJobQueue() {
         List<Job> allJobs = jobDAO.getAllJobs().stream()
                              .filter(j -> !STATE_PENDING_APPROVAL.equals(j.getStatus()))
                              .collect(Collectors.toList());
        allJobs.sort(Comparator.comparing(Job::getCreatedTimestamp));

        Job inProgressJob = null;
        List<Job> queuedJobs = new ArrayList<>();
        Map<String, String> currentStates = loadJobStates();

        for (Job job : allJobs) {
             String status = currentStates.getOrDefault(job.getJobId(), job.getStatus());
            if (STATE_PROGRESS.equals(status)) {
                inProgressJob = job;
            } else if (STATE_QUEUED.equals(status)) {
                queuedJobs.add(job);
            }
        }

        String nextJobId = null;
        if (inProgressJob != null) {
            inProgressJob.setStatus(STATE_COMPLETED);
            jobDAO.updateJob(inProgressJob);
            currentStates.put(inProgressJob.getJobId(), STATE_COMPLETED);
            logger.info("Advanced queue: Job " + inProgressJob.getJobId() + " marked as Completed.");

            if (!queuedJobs.isEmpty()) {
                Job nextJob = queuedJobs.get(0);
                nextJob.setStatus(STATE_PROGRESS);
                jobDAO.updateJob(nextJob);
                currentStates.put(nextJob.getJobId(), STATE_PROGRESS);
                nextJobId = nextJob.getJobId();
                logger.info("Advanced queue: Job " + nextJobId + " set to In Progress.");
            }
        } else if (!queuedJobs.isEmpty()) {
            Job nextJob = queuedJobs.get(0);
            nextJob.setStatus(STATE_PROGRESS);
            jobDAO.updateJob(nextJob);
            currentStates.put(nextJob.getJobId(), STATE_PROGRESS);
            nextJobId = nextJob.getJobId();
            logger.info("Advanced queue: No job was In Progress. Job " + nextJobId + " set to In Progress.");
        } else {
            logger.info("Advanced queue: No job In Progress and no Queued jobs found.");
        }

        saveJobStates(currentStates);
        calculateCompletionTimes(); // Recalculate schedule

        return nextJobId;
    }

    /**
     * Gets a summary of the current job queue status (including pending).
     * @return A map with count of jobs in each state.
     */
    public Map<String, Integer> getJobQueueSummary() {
        List<Job> allJobs = jobDAO.getAllJobs();
        Map<String, String> currentStates = loadJobStates();
        Map<String, Integer> summary = new HashMap<>();
        summary.put(STATE_PENDING_APPROVAL, 0);
        summary.put(STATE_QUEUED, 0);
        summary.put(STATE_PROGRESS, 0);
        summary.put(STATE_COMPLETED, 0);

         // Count pending from the synchronized list
         synchronized (pendingRequests) {
             summary.put(STATE_PENDING_APPROVAL, pendingRequests.size());
         }

         // Count other states from saved jobs
        for (Job job : allJobs) {
             if (!STATE_PENDING_APPROVAL.equals(job.getStatus())) { // Only count non-pending from files
                 String state = currentStates.getOrDefault(job.getJobId(), job.getStatus());
                 if (summary.containsKey(state)) {
                     summary.put(state, summary.get(state) + 1);
                 } else {
                     logger.warning("Job " + job.getJobId() + " (from file) has unknown state: " + state);
                 }
             }
        }
        return summary;
    }

    /**
     * Saves the calculated job schedule to a file.
     * @param completionTimes Map of job IDs to completion times.
     * @return true if saved successfully, false otherwise.
     */
    private boolean saveSchedule(Map<String, String> completionTimes) {
        List<String> lines = new ArrayList<>();
        completionTimes.forEach((jobId, time) -> lines.add(jobId + SEPARATOR + time));
        return FileManager.writeAllLines(SCHEDULE_FILE, lines);
    }

    /**
     * Saves the job states to a file (excluding pending).
     * @param jobStates Map of job IDs to states.
     * @return true if saved successfully, false otherwise.
     */
    private boolean saveJobStates(Map<String, String> jobStates) {
        List<String> lines = new ArrayList<>();
        jobStates.entrySet().stream()
            .filter(entry -> !STATE_PENDING_APPROVAL.equals(entry.getValue())) // Don't save pending state
            .forEach(entry -> lines.add(entry.getKey() + SEPARATOR + entry.getValue()));
        return FileManager.writeAllLines(JOB_STATE_FILE, lines);
    }

    /**
     * Loads the job schedule from file.
     * @return Map of job IDs to completion times.
     */
    public Map<String, String> loadSchedule() {
        Map<String, String> completionTimes = new LinkedHashMap<>();
        List<String> lines = FileManager.readAllLines(SCHEDULE_FILE);
        for (String line : lines) {
            String[] parts = line.split(DELIMITER, 2);
            if (parts.length == 2) {
                completionTimes.put(parts[0], parts[1]);
            } else {
                logger.warning("Skipping malformed line in schedule file: " + line);
            }
        }
        return completionTimes;
    }

    /**
     * Loads the job states from file.
     * @return Map of job IDs to their last known state (Queued, In Progress, Completed).
     */
    public Map<String, String> loadJobStates() {
        Map<String, String> jobStates = new HashMap<>();
        List<String> lines = FileManager.readAllLines(JOB_STATE_FILE);
        for (String line : lines) {
            String[] parts = line.split(DELIMITER, 2);
            if (parts.length == 2) {
                jobStates.put(parts[0], parts[1]);
            } else {
                logger.warning("Skipping malformed line in job state file: " + line);
            }
        }
        return jobStates;
    }

    /**
     * Gets a specific job's completion time from the loaded schedule.
     * @param jobId The ID of the job.
     * @return The completion time as a string, or null if not found.
     */
    public String getJobCompletionTime(String jobId) {
        return loadSchedule().get(jobId);
    }

    /**
     * Generate a formatted text output showing completion time for all saved jobs.
     * @return Formatted output string showing job scheduling results.
     */
    public String generateSchedulingOutput() {
         List<Job> allJobs = jobDAO.getAllJobs().stream()
                              .filter(j -> !STATE_PENDING_APPROVAL.equals(j.getStatus()))
                              .collect(Collectors.toList());
        allJobs.sort(Comparator.comparing(Job::getCreatedTimestamp));

        Map<String, String> completionTimes = loadSchedule();
        Map<String, String> currentStates = loadJobStates();

        StringBuilder output = new StringBuilder();
        output.append("Job Scheduling Results (FIFO - Excluding Pending)\n");
        output.append("=================================================\n");
        String headerFormat = "%-8s | %-10s | %-16s | %-19s | %s\n";
        String rowFormat =    "%-8s | %-10s | %-16s | %-19s | %s\n";
        output.append(String.format(headerFormat, "Job ID", "Duration", "Time Remaining", "Est. Compl. Time", "Status"));
        output.append("----------------------------------------------------------------------\n");

        long runningTotalMinutes = 0;

        for (Job job : allJobs) {
            Duration jobDuration = parseJobDuration(job);
            long durationMinutes = jobDuration.toMinutes();
            String status = currentStates.getOrDefault(job.getJobId(), job.getStatus());
            String completionTimeStr = completionTimes.getOrDefault(job.getJobId(), "-");
            String timeToCompleteStr = "-";

             if (STATE_QUEUED.equals(status) || STATE_PROGRESS.equals(status)) {
                 runningTotalMinutes += durationMinutes;
                 long totalHours = runningTotalMinutes / 60;
                 long totalMinutesPart = runningTotalMinutes % 60;
                 timeToCompleteStr = totalHours > 0
                        ? String.format("%dh %dm", totalHours, totalMinutesPart)
                        : String.format("%dm", totalMinutesPart);
             } else if (STATE_COMPLETED.equals(status)) {
                 timeToCompleteStr = "Completed";
             }

            output.append(String.format(rowFormat,
                    job.getJobId(),
                    job.getDuration(),
                    timeToCompleteStr,
                    completionTimeStr,
                    status));
        }
        return output.toString();
    }

    /**
     * Assigns available vehicles to jobs based on FIFO order (only affects saved jobs).
     * @return The number of assignments made.
     */
    public int assignVehiclesToJobs() {
         List<Job> jobs = jobDAO.getAllJobs().stream()
                          .filter(j -> !STATE_PENDING_APPROVAL.equals(j.getStatus()))
                          .collect(Collectors.toList());
        List<Vehicle> vehicles = vehicleDAO.getAllVehicles();
        Map<String, String> currentStates = loadJobStates();

        List<Job> queuedJobs = jobs.stream()
                .filter(job -> STATE_QUEUED.equals(currentStates.getOrDefault(job.getJobId(), job.getStatus())))
                .sorted(Comparator.comparing(Job::getCreatedTimestamp))
                .collect(Collectors.toList());

        List<Vehicle> availableVehicles = new ArrayList<>(vehicles);

        int assignmentCount = 0;
        for (Job job : queuedJobs) {
            if (assignmentCount < availableVehicles.size()) {
                job.setStatus(STATE_PROGRESS);
                jobDAO.updateJob(job);
                currentStates.put(job.getJobId(), STATE_PROGRESS);
                assignmentCount++;
                logger.info("Assigning vehicle to Job ID: " + job.getJobId() + " (Status set to In Progress)");
            } else {
                logger.info("No more available vehicles to assign.");
                break;
            }
        }

        if (assignmentCount > 0) {
             saveJobStates(currentStates);
             calculateCompletionTimes();
             logger.info("Assigned vehicles to " + assignmentCount + " jobs.");
        } else {
             logger.info("No vehicles assigned in this run.");
        }
        return assignmentCount;
    }
}