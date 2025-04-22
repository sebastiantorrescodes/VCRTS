package models;

public class PendingRequest {
    public enum RequestType { JOB, VEHICLE }

    private static int nextId = 1; // Simple ID generation for the simulation

    private int requestId;
    private RequestType type;
    private Object data; // Will hold either a Job or Vehicle object
    private String submittedByInfo; // e.g., "User ID: 1 (Job Owner)"

    public PendingRequest(RequestType type, Object data, User submittedBy) {
        this.requestId = nextId++;
        this.type = type;
        this.data = data;
        if (submittedBy != null) {
            this.submittedByInfo = String.format("User ID: %d (%s)", submittedBy.getUserId(), submittedBy.getFullName());
        } else {
            this.submittedByInfo = "Unknown";
        }
    }
public static int getNextId() {
    return nextId;
}

public static void setNextId(int id) {
    nextId = id;
}
    // Getters
    public int getRequestId() { return requestId; }
    public RequestType getType() { return type; }
    public Object getData() { return data; }
    public String getSubmittedByInfo() { return submittedByInfo; }

    @Override
    public String toString() {
        String dataDetails = "";
        if (type == RequestType.JOB && data instanceof Job) {
            Job job = (Job) data;
            dataDetails = String.format("Job ID: %s, Name: %s", job.getJobId(), job.getJobName());
        } else if (type == RequestType.VEHICLE && data instanceof Vehicle) {
            Vehicle vehicle = (Vehicle) data;
            dataDetails = String.format("VIN: %s, Make: %s, Model: %s", vehicle.getVin(), vehicle.getMake(), vehicle.getModel());
        }
        return String.format("Req ID: %d, Type: %s, Submitted by: %s, Details: [%s]",
                requestId, type, submittedByInfo, dataDetails);
    }
}