package gui.server;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Iterator;

import dao.JobDAO;
import dao.UserDAO;
import dao.VehicleDAO;
import dao.AllocationDAO;
import dao.CloudControllerDAO;
import models.Job;
import models.PendingRequest;
import models.User;
import models.Allocation;
import models.Vehicle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class CloudControllerDashboard extends JPanel {
    private JTable jobTable, userTable, allocationTable, scheduleTable, pendingRequestTable;
    private DefaultTableModel jobTableModel, userTableModel, allocationTableModel, scheduleTableModel, pendingRequestTableModel;
    private JButton addJobButton, editJobButton, deleteJobButton;
    private JButton addUserButton, editUserButton, deleteUserButton;
    private JButton allocateButton, removeAllocationButton;
    private JButton calculateTimesButton, assignVehiclesButton, advanceQueueButton;
    private JButton approveRequestButton, rejectRequestButton;
    private JComboBox<String> userDropdown, jobDropdown;
    private JLabel queueStatusLabel;
    
    // DAO instances
    private JobDAO jobDAO = new JobDAO();
    private UserDAO userDAO = new UserDAO();
    private AllocationDAO allocationDAO = new AllocationDAO();
    private CloudControllerDAO cloudControllerDAO = new CloudControllerDAO();
    
    // Socket server fields
    private ServerSocket serverSocket; 
    private final int PORT = 9876; 
    private boolean isRunning = true; 
    private List<ClientHandler> clientHandlers = new ArrayList<>();
    
    // In-memory storage for pending requests
    private List<PendingRequest> pendingRequests = new ArrayList<>();
    private int nextRequestId = 1;
    
    public CloudControllerDashboard() {
        // Start socket server first
        startSocketServer();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopSocketServer));
        
        setLayout(new BorderLayout());
        setBackground(Color.LIGHT_GRAY);
        
        // Top panel with title
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        topPanel.setBackground(new Color(43, 43, 43));
        JLabel titleLabel = new JLabel("Cloud Controller Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        topPanel.add(titleLabel);
        add(topPanel, BorderLayout.NORTH);

        // Tabbed pane for different views
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.BOLD, 14));

        // --- Pending Requests Tab ---
        JPanel pendingRequestPanel = createPendingRequestPanel();
        tabbedPane.addTab("Pending Approvals", UIManager.getIcon("OptionPane.questionIcon"), pendingRequestPanel);

        // --- Jobs Tab ---
        JPanel jobPanel = createJobPanel();
        tabbedPane.addTab("Jobs", UIManager.getIcon("FileView.hardDriveIcon"), jobPanel);

        // --- Users Tab ---
        JPanel userPanel = createUserPanel();
        tabbedPane.addTab("Users", UIManager.getIcon("FileChooser.detailsViewIcon"), userPanel);

        // --- Allocations Tab ---
        JPanel allocationPanel = createAllocationPanel();
        tabbedPane.addTab("Allocations", UIManager.getIcon("Tree.openIcon"), allocationPanel);

        // --- Schedule Tab ---
        JPanel schedulePanel = createSchedulePanel();
        tabbedPane.addTab("Job Schedule", UIManager.getIcon("Tree.leafIcon"), schedulePanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Load initial data
        refreshAllData();
        loadPendingRequestData(); // Initial load of in-memory pending requests

        tabbedPane.setSelectedIndex(0);
        
        // Set up refresh timer for pending requests UI
        new Timer(5000, e -> SwingUtilities.invokeLater(() -> loadPendingRequestData())).start();

        // Add tab change listener to refresh relevant data
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            switch (selectedIndex) {
                case 0: // Pending Approvals
                    loadPendingRequestData();
                    break;
                case 1: // Jobs
                    loadJobData();
                    break;
                case 2: // Users
                    loadUserData();
                    break;
                case 3: // Allocations
                    loadAllocationData();
                    loadAllocationDropdowns();
                    break;
                case 4: // Schedule
                    loadScheduleData();
                    updateQueueStatus();
                    break;
            }
        });
    }
    
    // --- Socket Server Methods ---
    
    public void startSocketServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("Cloud Controller socket server started on port " + PORT);
                
                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler handler = new ClientHandler(clientSocket);
                        clientHandlers.add(handler);
                        new Thread(handler).start();
                        System.out.println("New client connected: " + clientSocket.getInetAddress());
                    } catch (IOException e) {
                        if (isRunning) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    public void stopSocketServer() {
        isRunning = false;
        for (ClientHandler handler : clientHandlers) {
            handler.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("Socket server stopped");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // Send approval/rejection notifications to all clients
    private void broadcastApprovalStatus(String vin, boolean approved) {
        String message = "APPROVAL_STATUS:" + vin + "," + (approved ? "approved" : "rejected");
        System.out.println("Broadcasting: " + message);
        for (ClientHandler handler : clientHandlers) {
            handler.sendMessage(message);
        }
    }

    // Inner class to handle client connections
    private class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean running = true;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String inputLine;
                while (running && (inputLine = in.readLine()) != null) {
                    // Process incoming messages
                    processMessage(inputLine);
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            } finally {
                close();
            }
        }

        private void processMessage(String message) {
            System.out.println("Received message: " + message);
            
            // Handle vehicle registration with updated format
            if (message.startsWith("NEW_VEHICLE:")) {
                // Updated format: NEW_VEHICLE:ownerId,vehicleOwnerId,make,model,year,vin,residencyTime
                String[] parts = message.substring("NEW_VEHICLE:".length()).split(",");
                if (parts.length >= 7) {
                    try {
                        String ownerId = parts[0]; // Now a string
                        int vehicleOwnerId = Integer.parseInt(parts[1]); // Actual user ID
                        String make = parts[2];
                        String model = parts[3];
                        String year = parts[4];
                        String vin = parts[5];
                        String residencyTime = parts[6];
                        
                        // Create a Vehicle object with the new fields
                        Vehicle vehicle = new Vehicle(ownerId, vehicleOwnerId, model, make, year, vin, residencyTime);
                        
                        // Create a PendingRequest object
                        UserDAO userDAO = new UserDAO();
                        User submitter = userDAO.getUserById(vehicleOwnerId);
                        
                        PendingRequest request = new PendingRequest(
                            PendingRequest.RequestType.VEHICLE,
                            vehicle,
                            null // User object not needed
                        );
                        
                        // Set submitted by info manually since we don't have User object
                        String submitterInfo = "User ID: " + vehicleOwnerId;
                        if (submitter != null) {
                            submitterInfo += " (" + submitter.getFullName() + ")";
                        }
                        
                        // Use reflection to set the submittedByInfo field
                        try {
                            java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("submittedByInfo");
                            field.setAccessible(true);
                            field.set(request, submitterInfo);
                        } catch (Exception e) {
                            System.err.println("Error setting submittedByInfo: " + e.getMessage());
                        }
                        
                        // Add to in-memory list
                        synchronized(pendingRequests) {
                            // Set the request ID manually to ensure uniqueness
                            try {
                                java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("requestId");
                                field.setAccessible(true);
                                field.set(request, nextRequestId++);
                            } catch (Exception e) {
                                System.err.println("Error setting requestId: " + e.getMessage());
                            }
                            
                            pendingRequests.add(request);
                        }
                        
                        // Update the UI asynchronously
                        SwingUtilities.invokeLater(() -> {
                            loadPendingRequestData();
                            // Show notification about new request
                            JOptionPane.showMessageDialog(
                                CloudControllerDashboard.this,
                                "New vehicle registration request received!\nOwner id: "+ ownerId +", Make: " + make + ", Model: " + model + ", VIN: " + vin,
                                "New Request",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    } catch (Exception e) {
                        System.err.println("Error processing vehicle request: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            // Handle job submission
            else if (message.startsWith("NEW_JOB:")) {
                // Format: NEW_JOB:userId,jobId,jobName,duration,deadline
                String[] parts = message.substring("NEW_JOB:".length()).split(",");
                if (parts.length >= 5) {
                    try {
                        int userId = Integer.parseInt(parts[0]);
                        String jobId = parts[1];
                        String jobName = parts[2];
                        String duration = parts[3];
                        String deadline = parts[4];
                        
                        // Create a Job object
                        Job job = new Job(jobId, jobName, userId, duration, deadline, "");
                        
                        // Create a PendingRequest object
                        UserDAO userDAO = new UserDAO();
                        User submitter = userDAO.getUserById(userId);
                        
                        PendingRequest request = new PendingRequest(
                            PendingRequest.RequestType.JOB,
                            job,
                            null // User object not needed
                        );
                        
                        // Set submitted by info manually
                        String submitterInfo = "User ID: " + userId;
                        if (submitter != null) {
                            submitterInfo += " (" + submitter.getFullName() + ")";
                        }
                        
                        // Use reflection to set the submittedByInfo field
                        try {
                            java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("submittedByInfo");
                            field.setAccessible(true);
                            field.set(request, submitterInfo);
                        } catch (Exception e) {
                            System.err.println("Error setting submittedByInfo: " + e.getMessage());
                        }
                        
                        // Add to in-memory list
                        synchronized(pendingRequests) {
                            // Set the request ID manually to ensure uniqueness
                            try {
                                java.lang.reflect.Field field = PendingRequest.class.getDeclaredField("requestId");
                                field.setAccessible(true);
                                field.set(request, nextRequestId++);
                            } catch (Exception e) {
                                System.err.println("Error setting requestId: " + e.getMessage());
                            }
                            
                            pendingRequests.add(request);
                        }
                        
                        // Update the UI asynchronously
                        SwingUtilities.invokeLater(() -> {
                            loadPendingRequestData();
                            // Show notification about new request
                            JOptionPane.showMessageDialog(
                                CloudControllerDashboard.this,
                                "New job submission request received!\nID: " + jobId + ", Name: " + jobName + ", Duration: " + duration,
                                "New Request",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    } catch (Exception e) {
                        System.err.println("Error processing job request: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
                System.out.println("Sent to client: " + message);
            }
        }

        public void close() {
            running = false;
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
                clientHandlers.remove(this);
                System.out.println("Client handler closed");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    // --- Panel Creation Methods ---

    private JPanel createPendingRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(Color.WHITE);

        // Table setup
        String[] pendingColumns = {"Req ID", "Type", "Submitted By", "Data Details"};
        pendingRequestTableModel = new DefaultTableModel(pendingColumns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        pendingRequestTable = new JTable(pendingRequestTableModel);
        setupTableAppearance(pendingRequestTable);
        
        // Adjust column widths
        TableColumnModel colModel = pendingRequestTable.getColumnModel();
        colModel.getColumn(0).setPreferredWidth(60);  colModel.getColumn(0).setMaxWidth(80); // Req ID
        colModel.getColumn(1).setPreferredWidth(80);  colModel.getColumn(1).setMaxWidth(100); // Type
        colModel.getColumn(2).setPreferredWidth(180); // Submitted By
        colModel.getColumn(3).setPreferredWidth(400); // Data Details
        pendingRequestTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        JScrollPane scrollPane = new JScrollPane(pendingRequestTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Action panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        actionPanel.setBackground(Color.WHITE);
        approveRequestButton = new JButton("Approve Selected", UIManager.getIcon("OptionPane.informationIcon"));
        rejectRequestButton = new JButton("Reject Selected", UIManager.getIcon("OptionPane.errorIcon"));
        JButton refreshPendingButton = new JButton("Refresh List", UIManager.getIcon("Tree.closedIcon"));

        approveRequestButton.addActionListener(e -> approveSelectedRequest());
        rejectRequestButton.addActionListener(e -> rejectSelectedRequest());
        refreshPendingButton.addActionListener(e -> loadPendingRequestData());

        actionPanel.add(approveRequestButton);
        actionPanel.add(rejectRequestButton);
        actionPanel.add(refreshPendingButton);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    // Other panel creation methods remain the same...
    private JPanel createJobPanel() {
        // Your existing code...
        JPanel jobPanel = new JPanel(new BorderLayout(10, 10));
        jobPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        jobPanel.setBackground(Color.WHITE);

        String[] jobColumns = {"Job ID", "Job Name", "Job Owner ID", "Duration", "Deadline", "Status", "Created At"};
        jobTableModel = new DefaultTableModel(jobColumns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        jobTable = new JTable(jobTableModel);
        setupTableAppearance(jobTable);
        jobPanel.add(new JScrollPane(jobTable), BorderLayout.CENTER);

        JPanel jobActionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        jobActionPanel.setBackground(Color.WHITE);
        addJobButton = new JButton("Add Job (Direct)");
        editJobButton = new JButton("Edit Job");
        deleteJobButton = new JButton("Delete Job");
        JButton refreshJobsButton = new JButton("Refresh Jobs");

        addJobButton.addActionListener(e -> addNewJob());
        editJobButton.addActionListener(e -> editSelectedJob());
        deleteJobButton.addActionListener(e -> deleteSelectedJob());
        refreshJobsButton.addActionListener(e -> loadJobData());

        jobActionPanel.add(addJobButton);
        jobActionPanel.add(editJobButton);
        jobActionPanel.add(deleteJobButton);
        jobActionPanel.add(refreshJobsButton);
        jobPanel.add(jobActionPanel, BorderLayout.SOUTH);
        return jobPanel;
    }

    private JPanel createUserPanel() {
        // Your existing code...
        JPanel userPanel = new JPanel(new BorderLayout(10, 10));
        userPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        userPanel.setBackground(Color.WHITE);

        String[] userColumns = {"User ID", "Full Name", "Email", "Roles"};
        userTableModel = new DefaultTableModel(userColumns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        userTable = new JTable(userTableModel);
        setupTableAppearance(userTable);
        userPanel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        JPanel userActionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        userActionPanel.setBackground(Color.WHITE);
        addUserButton = new JButton("Add User");
        editUserButton = new JButton("Edit User");
        deleteUserButton = new JButton("Delete User");
        JButton refreshUsersButton = new JButton("Refresh Users");

        addUserButton.addActionListener(e -> addNewUser());
        editUserButton.addActionListener(e -> editSelectedUser());
        deleteUserButton.addActionListener(e -> deleteSelectedUser());
        refreshUsersButton.addActionListener(e -> loadUserData());

        userActionPanel.add(addUserButton);
        userActionPanel.add(editUserButton);
        userActionPanel.add(deleteUserButton);
        userActionPanel.add(refreshUsersButton);
        userPanel.add(userActionPanel, BorderLayout.SOUTH);
        return userPanel;
    }

    private JPanel createAllocationPanel() {
        // Your existing code...
        JPanel allocationPanel = new JPanel(new BorderLayout(10, 10));
        allocationPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        allocationPanel.setBackground(Color.WHITE);

        String[] allocationColumns = {"Alloc ID", "User ID", "Job ID"};
        allocationTableModel = new DefaultTableModel(allocationColumns, 0){
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        allocationTable = new JTable(allocationTableModel);
        setupTableAppearance(allocationTable);
        allocationPanel.add(new JScrollPane(allocationTable), BorderLayout.CENTER);

        JPanel allocationControls = new JPanel(new GridBagLayout());
        allocationControls.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        userDropdown = new JComboBox<>();
        userDropdown.setPrototypeDisplayValue("User ID - User NameXXXXXXXXXX");
        jobDropdown = new JComboBox<>();
        jobDropdown.setPrototypeDisplayValue("Job ID - Job NameXXXXXXXXXXXXX");

        allocateButton = new JButton("Allocate User to Job");
        removeAllocationButton = new JButton("Remove Selected Allocation");
        JButton refreshAllocationsButton = new JButton("Refresh Allocations");

        allocateButton.addActionListener(e -> allocateUserToJob());
        removeAllocationButton.addActionListener(e -> removeSelectedAllocation());
        refreshAllocationsButton.addActionListener(e -> {
            loadAllocationData();
            loadAllocationDropdowns();
        });

        gbc.gridx = 0; gbc.gridy = 0; allocationControls.add(new JLabel("Select User:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx=1.0; gbc.fill=GridBagConstraints.HORIZONTAL; allocationControls.add(userDropdown, gbc);
        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx=0; gbc.fill=GridBagConstraints.NONE; allocationControls.add(allocateButton, gbc);

        gbc.gridx = 0; gbc.gridy = 1; allocationControls.add(new JLabel("Select Job:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx=1.0; gbc.fill=GridBagConstraints.HORIZONTAL; allocationControls.add(jobDropdown, gbc);
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx=0; gbc.fill=GridBagConstraints.NONE; allocationControls.add(removeAllocationButton, gbc);

        gbc.gridx = 2; gbc.gridy = 2; allocationControls.add(refreshAllocationsButton, gbc);

        allocationPanel.add(allocationControls, BorderLayout.SOUTH);
        return allocationPanel;
    }

    private JPanel createSchedulePanel() {
        // Your existing code...
        JPanel schedulePanel = new JPanel(new BorderLayout(10, 10));
        schedulePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        schedulePanel.setBackground(Color.WHITE);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusPanel.setBackground(Color.WHITE);
        queueStatusLabel = new JLabel("Queue Status: Loading...", SwingConstants.CENTER);
        queueStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        queueStatusLabel.setForeground(new Color(0, 102, 204));
        statusPanel.add(queueStatusLabel);
        schedulePanel.add(statusPanel, BorderLayout.NORTH);

        String[] scheduleColumns = {"Job ID", "Job Name", "Duration", "Time Remaining", "Status", "Est. Completion Time"};
        scheduleTableModel = new DefaultTableModel(scheduleColumns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        scheduleTable = new JTable(scheduleTableModel);
        setupTableAppearance(scheduleTable);
        schedulePanel.add(new JScrollPane(scheduleTable), BorderLayout.CENTER);

        JPanel scheduleControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        scheduleControlPanel.setBackground(Color.WHITE);
        calculateTimesButton = new JButton("Recalculate Schedule");
        assignVehiclesButton = new JButton("Assign Vehicles");
        advanceQueueButton = new JButton("Advance Job Queue");

        calculateTimesButton.addActionListener(e -> calculateCompletionTimes());
        assignVehiclesButton.addActionListener(e -> assignVehiclesToJobs());
        advanceQueueButton.addActionListener(e -> advanceJobQueue());

        scheduleControlPanel.add(calculateTimesButton);
        scheduleControlPanel.add(assignVehiclesButton);
        scheduleControlPanel.add(advanceQueueButton);
        schedulePanel.add(scheduleControlPanel, BorderLayout.SOUTH);
        return schedulePanel;
    }

    // --- Data Loading and Refresh Methods ---

    private void refreshAllData() {
        loadJobData();
        loadUserData();
        loadAllocationData();
        loadAllocationDropdowns();
        loadScheduleData();
        updateQueueStatus();
    }

    private void loadPendingRequestData() {
        System.out.println("Loading pending requests...");
        pendingRequestTableModel.setRowCount(0);
        
        synchronized(pendingRequests) {
            System.out.println("Found " + pendingRequests.size() + " pending requests");
            
            for (PendingRequest req : pendingRequests) {
                String details = "";
                if (req.getType() == PendingRequest.RequestType.VEHICLE) {
                    Vehicle vehicle = (Vehicle) req.getData();
                    details = String.format("Owner ID: %s, VIN: %s, %s %s (%s)", 
                        vehicle.getOwnerId(), vehicle.getVin(), vehicle.getMake(), vehicle.getModel(), vehicle.getYear());
                } else if (req.getType() == PendingRequest.RequestType.JOB) {
                    Job job = (Job) req.getData();
                    details = String.format("ID: %s, Name: %s, Duration: %s", 
                        job.getJobId(), job.getJobName(), job.getDuration());
                }
                
                pendingRequestTableModel.addRow(new Object[]{
                    req.getRequestId(),
                    req.getType(),
                    req.getSubmittedByInfo(),
                    details
                });
            }
        }
        
        boolean hasRequests = pendingRequestTableModel.getRowCount() > 0;
        approveRequestButton.setEnabled(hasRequests);
        rejectRequestButton.setEnabled(hasRequests);
    }

    private void loadJobData() {
        // Your existing code...
        jobTableModel.setRowCount(0);
        Map<String, String> currentStates = cloudControllerDAO.loadJobStates();
        List<Job> jobs = jobDAO.getAllJobs();

        jobs.sort(Comparator.comparing(Job::getCreatedTimestamp));

        for (Job job : jobs) {
            String displayStatus = CloudControllerDAO.STATE_PENDING_APPROVAL.equals(job.getStatus())
                               ? CloudControllerDAO.STATE_PENDING_APPROVAL
                               : currentStates.getOrDefault(job.getJobId(), job.getStatus());

            jobTableModel.addRow(new Object[]{
                job.getJobId(),
                job.getJobName(),
                job.getJobOwnerId(),
                job.getDuration(),
                job.getDeadline(),
                displayStatus,
                job.getCreatedTimestamp()
            });
        }
    }

    private void loadUserData() {
        // Your existing code...
        userTableModel.setRowCount(0);
        List<User> users = userDAO.getAllUsers();
        for (User user : users) {
            userTableModel.addRow(new Object[]{
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getRolesAsString()
            });
        }
    }

    private void loadAllocationData() {
        // Your existing code...
        allocationTableModel.setRowCount(0);
        List<Allocation> allocations = allocationDAO.getAllAllocations();
        for (Allocation allocation : allocations) {
            allocationTableModel.addRow(new Object[]{
                allocation.getAllocationId(),
                allocation.getUserId(),
                allocation.getJobId()
            });
        }
    }

    private void loadScheduleData() {
        // Your existing code...
        scheduleTableModel.setRowCount(0);
        Map<String, String> completionTimes = cloudControllerDAO.loadSchedule();
        Map<String, String> currentStates = cloudControllerDAO.loadJobStates();

        List<Job> jobs = jobDAO.getAllJobs().stream()
                       .filter(j -> !CloudControllerDAO.STATE_PENDING_APPROVAL.equals(j.getStatus()))
                       .collect(Collectors.toList());

        jobs.sort(Comparator.comparing(Job::getCreatedTimestamp));

        long runningTotalMinutes = 0;

        for (Job job : jobs) {
            // Use the public parseJobDuration method from CloudControllerDAO
            long durationMinutes = cloudControllerDAO.parseJobDuration(job).toMinutes();

            String status = currentStates.getOrDefault(job.getJobId(), job.getStatus());
            String timeToCompleteStr = "-";

            if (CloudControllerDAO.STATE_QUEUED.equals(status) || CloudControllerDAO.STATE_PROGRESS.equals(status)) {
                runningTotalMinutes += durationMinutes;
                long totalHours = runningTotalMinutes / 60;
                long totalMinutesPart = runningTotalMinutes % 60;
                timeToCompleteStr = totalHours > 0
                    ? String.format("%dh %dm", totalHours, totalMinutesPart)
                    : String.format("%dm", totalMinutesPart);
            } else if (CloudControllerDAO.STATE_COMPLETED.equals(status)) {
                timeToCompleteStr = "Completed";
            }

            String completionTime = completionTimes.getOrDefault(job.getJobId(), "Not calculated");

            scheduleTableModel.addRow(new Object[]{
                job.getJobId(),
                job.getJobName(),
                job.getDuration(),
                timeToCompleteStr,
                status,
                completionTime
            });
        }
    }

    private void updateQueueStatus() {
        Map<String, Integer> summary = cloudControllerDAO.getJobQueueSummary();
        
        // Add pending count from our in-memory list
        int pendingCount;
        synchronized(pendingRequests) {
            pendingCount = (int) pendingRequests.stream()
                .filter(r -> r.getType() == PendingRequest.RequestType.JOB)
                .count();
        }
        
        queueStatusLabel.setText(String.format(
            "Queue Status: %d Pending | %d Queued | %d In Progress | %d Completed",
            pendingCount,
            summary.getOrDefault(CloudControllerDAO.STATE_QUEUED, 0),
            summary.getOrDefault(CloudControllerDAO.STATE_PROGRESS, 0),
            summary.getOrDefault(CloudControllerDAO.STATE_COMPLETED, 0)
        ));
    }

    private void loadAllocationDropdowns() {
        // Your existing code...
        userDropdown.removeAllItems();
        jobDropdown.removeAllItems();

        List<User> users = userDAO.getAllUsers();
        users.sort(Comparator.comparing(User::getFullName));
        for (User user : users) {
            userDropdown.addItem(user.getUserId() + " - " + user.getFullName());
        }

        List<Job> jobs = jobDAO.getAllJobs().stream()
                       .filter(j -> !CloudControllerDAO.STATE_PENDING_APPROVAL.equals(j.getStatus()))
                       .sorted(Comparator.comparing(Job::getJobId))
                       .collect(Collectors.toList());
        for (Job job : jobs) {
            jobDropdown.addItem(job.getJobId() + " - " + job.getJobName());
        }
    }

    // --- Action Methods ---

    private void approveSelectedRequest() {
        int selectedRow = pendingRequestTable.getSelectedRow();
        if (selectedRow != -1) {
            int requestId = (int) pendingRequestTableModel.getValueAt(selectedRow, 0);
            // Get the type as the actual enum value, not as a String
            PendingRequest.RequestType typeEnum = (PendingRequest.RequestType) pendingRequestTableModel.getValueAt(selectedRow, 1);
            
            // Find the request in our in-memory list
            PendingRequest requestToApprove = null;
            synchronized(pendingRequests) {
                for (PendingRequest req : pendingRequests) {
                    if (req.getRequestId() == requestId) {
                        requestToApprove = req;
                        break;
                    }
                }
            }
            
            if (requestToApprove != null) {
                // Process the approval based on type
                if (requestToApprove.getType() == PendingRequest.RequestType.VEHICLE) {
                    Vehicle vehicle = (Vehicle) requestToApprove.getData();
                    VehicleDAO vehicleDAO = new VehicleDAO();
                    
                    // Save to database directly without validation
                    boolean saved = vehicleDAO.addVehicle(vehicle);
                    
                    if (saved) {
                        // Remove from pending list
                        synchronized(pendingRequests) {
                            pendingRequests.remove(requestToApprove);
                        }
                        
                        // Send approval notification to clients
                        broadcastApprovalStatus(vehicle.getVin(), true);
                        
                        // Update UI
                        loadPendingRequestData();
                        JOptionPane.showMessageDialog(this, 
                            "Vehicle approved and saved to database.",
                            "Approval Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, 
                            "Error saving vehicle to database.",
                            "Database Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } else if (requestToApprove.getType() == PendingRequest.RequestType.JOB) {
                    Job job = (Job) requestToApprove.getData();
                    JobDAO jobDAO = new JobDAO();
                    
                    // Set status to Queued
                    job.setStatus(CloudControllerDAO.STATE_QUEUED);
                    
                    // Save to database without validation
                    boolean saved = jobDAO.addJob(job);
                    
                    if (saved) {
                        // Remove from pending list
                        synchronized(pendingRequests) {
                            pendingRequests.remove(requestToApprove);
                        }
                        
                        // Send approval notification to clients
                        String message = "JOB_APPROVAL_STATUS:" + job.getJobId() + ",approved";
                        for (ClientHandler handler : clientHandlers) {
                            handler.sendMessage(message);
                        }
                        
                        // Update UI
                        loadPendingRequestData();
                        loadJobData();
                        loadScheduleData();
                        updateQueueStatus();
                        
                        JOptionPane.showMessageDialog(this, 
                            "Job approved and saved to database.",
                            "Approval Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, 
                            "Error saving job to database.",
                            "Database Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Request not found in pending list.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this, 
                "Please select a request to approve.",
                "Selection Required",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void rejectSelectedRequest() {
        int selectedRow = pendingRequestTable.getSelectedRow();
        if (selectedRow != -1) {
            int requestId = (int) pendingRequestTableModel.getValueAt(selectedRow, 0);
            // Get the type as the actual enum value, not as a String
            PendingRequest.RequestType typeEnum = (PendingRequest.RequestType) pendingRequestTableModel.getValueAt(selectedRow, 1);
            String details = (String) pendingRequestTableModel.getValueAt(selectedRow, 3);
            
            // Extract VIN if it's a vehicle request
            String vin = null;
            if (typeEnum == PendingRequest.RequestType.VEHICLE && details.contains("VIN:")) {
                vin = details.substring(details.indexOf("VIN:") + 5);
                if (vin.contains(",")) {
                    vin = vin.substring(0, vin.indexOf(",")).trim();
                } else if (vin.contains(")")) {
                    vin = vin.substring(0, vin.indexOf(")")).trim();
                }
            }
            
            // Extract Job ID if it's a job request
            String jobId = null;
            if (typeEnum == PendingRequest.RequestType.JOB && details.contains("ID:")) {
                jobId = details.substring(details.indexOf("ID:") + 3);
                if (jobId.contains(",")) {
                    jobId = jobId.substring(0, jobId.indexOf(",")).trim();
                }
            }
            
            int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to reject this request?",
                "Confirm Rejection",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (confirm == JOptionPane.YES_OPTION) {
                // Find and remove the request
                boolean removed = false;
                synchronized(pendingRequests) {
                    Iterator<PendingRequest> iterator = pendingRequests.iterator();
                    while (iterator.hasNext()) {
                        PendingRequest req = iterator.next();
                        if (req.getRequestId() == requestId) {
                            iterator.remove();
                            removed = true;
                            break;
                        }
                    }
                }
                
                if (removed) {
                    // Notify client based on request type
                    if (vin != null) {
                        broadcastApprovalStatus(vin, false);
                    } else if (jobId != null) {
                        String message = "JOB_APPROVAL_STATUS:" + jobId + ",rejected";
                        for (ClientHandler handler : clientHandlers) {
                            handler.sendMessage(message);
                        }
                    }
                    
                    // Update UI
                    loadPendingRequestData();
                    updateQueueStatus();
                    JOptionPane.showMessageDialog(this,
                        "Request rejected and removed.",
                        "Rejection Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Error: Request not found in pending list.",
                        "Rejection Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a request to reject.",
                "Selection Required",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void calculateCompletionTimes() {
        // Your existing code...
        Map<String, String> completionTimes = cloudControllerDAO.calculateCompletionTimes();
        if (completionTimes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No jobs found to schedule.", "Schedule", JOptionPane.INFORMATION_MESSAGE);
        } else {
            loadScheduleData();
            loadJobData();
            updateQueueStatus();

            // Show the calculation results
            String output = cloudControllerDAO.generateSchedulingOutput();
            JTextArea textArea = new JTextArea(output);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 300));

            JOptionPane.showMessageDialog(this,
                    scrollPane,
                    "Job Scheduling Results (FIFO)",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void assignVehiclesToJobs() {
        // Your existing code...
        int assignmentCount = cloudControllerDAO.assignVehiclesToJobs();
        if (assignmentCount > 0) {
            loadJobData();
            loadScheduleData();
            updateQueueStatus();
            JOptionPane.showMessageDialog(this,
                    "Assigned vehicles to " + assignmentCount + " job(s). Statuses updated to 'In Progress'.",
                    "Vehicle Assignment Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "No new vehicle assignments made.\nCheck job queue and vehicle availability.",
                    "Vehicle Assignment Info",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void advanceJobQueue() {
        // Your existing code...
        String nextJobId = cloudControllerDAO.advanceJobQueue();
        if (nextJobId != null) {
            loadJobData();
            loadScheduleData();
            updateQueueStatus();
            JOptionPane.showMessageDialog(this,
                    "Job queue advanced. Job '" + nextJobId + "' is now 'In Progress'.",
                    "Queue Advanced",
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Could not advance job queue. Check for 'In Progress' or 'Queued' jobs.",
                    "Queue Status",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --- Direct Add/Edit/Delete Methods ---
    // These methods can remain unchanged...
    private void addNewJob() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcDialog = new GridBagConstraints();
        gbcDialog.insets = new Insets(5,5,5,5); gbcDialog.anchor = GridBagConstraints.WEST;

        JTextField jobIdField = new JTextField(15);
        JTextField jobNameField = new JTextField(15);
        JTextField jobOwnerField = new JTextField(15);
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(0, 0, 99, 1); JSpinner hoursSpinner = new JSpinner(hoursModel);
        SpinnerNumberModel minutesModel = new SpinnerNumberModel(0, 0, 59, 1); JSpinner minutesSpinner = new JSpinner(minutesModel);
        SpinnerNumberModel secondsModel = new SpinnerNumberModel(0, 0, 59, 1); JSpinner secondsSpinner = new JSpinner(secondsModel);
        JTextField deadlineField = new JTextField(10); deadlineField.setText(java.time.LocalDate.now().plusDays(7).toString());

        gbcDialog.gridx=0; gbcDialog.gridy=0; panel.add(new JLabel("Job ID:"), gbcDialog); gbcDialog.gridx=1; panel.add(jobIdField, gbcDialog);
        gbcDialog.gridx=0; gbcDialog.gridy=1; panel.add(new JLabel("Job Name:"), gbcDialog); gbcDialog.gridx=1; panel.add(jobNameField, gbcDialog);
        gbcDialog.gridx=0; gbcDialog.gridy=2; panel.add(new JLabel("Job Owner ID:"), gbcDialog); gbcDialog.gridx=1; panel.add(jobOwnerField, gbcDialog);
        gbcDialog.gridx=0; gbcDialog.gridy=3; panel.add(new JLabel("Duration:"), gbcDialog);
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        durationPanel.add(hoursSpinner); durationPanel.add(new JLabel("h")); durationPanel.add(minutesSpinner); durationPanel.add(new JLabel("m")); durationPanel.add(secondsSpinner); durationPanel.add(new JLabel("s"));
        gbcDialog.gridx=1; panel.add(durationPanel, gbcDialog);
        gbcDialog.gridx=0; gbcDialog.gridy=4; panel.add(new JLabel("Deadline (YYYY-MM-DD):"), gbcDialog); gbcDialog.gridx=1; panel.add(deadlineField, gbcDialog);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add New Job (Directly)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            try {
                String jobId = jobIdField.getText().trim(); 
                String jobName = jobNameField.getText().trim(); 
                int jobOwner = Integer.parseInt(jobOwnerField.getText().trim());
                int hours = (Integer) hoursSpinner.getValue(); 
                int minutes = (Integer) minutesSpinner.getValue(); 
                int seconds = (Integer) secondsSpinner.getValue();
                String duration = String.format("%02d:%02d:%02d", hours, minutes, seconds); 
                String deadline = deadlineField.getText().trim();

                if (jobId.isEmpty() || jobName.isEmpty() || duration.equals("00:00:00") || !deadline.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    JOptionPane.showMessageDialog(this, "Valid Job ID, Name, Duration, and Deadline (YYYY-MM-DD) required.", "Input Error", JOptionPane.ERROR_MESSAGE); 
                    return;
                }
                Job job = new Job(jobId, jobName, jobOwner, duration, deadline, CloudControllerDAO.STATE_QUEUED);
                if (jobDAO.addJob(job)) {
                    JOptionPane.showMessageDialog(this, "Job added directly (bypassing approval).", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshAllData();
                } else { 
                    JOptionPane.showMessageDialog(this, "Failed to add job directly.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            } catch (NumberFormatException e) { 
                JOptionPane.showMessageDialog(this, "Invalid Job Owner ID.", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception e) { 
                JOptionPane.showMessageDialog(this, "An error occurred: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
            }
        }
    }

    private void editSelectedJob() {
        int selectedRow = jobTable.getSelectedRow();
        if (selectedRow != -1) {
            String jobId = (String) jobTableModel.getValueAt(selectedRow, 0);
            Job jobToEdit = jobDAO.getAllJobs().stream().filter(j -> j.getJobId().equals(jobId)).findFirst().orElse(null);
            if (jobToEdit == null) { 
                JOptionPane.showMessageDialog(this, "Could not find job details.", "Error", JOptionPane.ERROR_MESSAGE); 
                return; 
            }

            JTextField nameField = new JTextField(jobToEdit.getJobName(), 20);
            String[] statuses = {CloudControllerDAO.STATE_QUEUED, CloudControllerDAO.STATE_PROGRESS, CloudControllerDAO.STATE_COMPLETED};
            JComboBox<String> statusCombo = new JComboBox<>(statuses);
            statusCombo.setSelectedItem(jobToEdit.getStatus());
            
            JPanel panel = new JPanel(new GridLayout(0,2,5,5));
            panel.add(new JLabel("Job Name:")); panel.add(nameField);
            panel.add(new JLabel("Status:")); panel.add(statusCombo);

            int result = JOptionPane.showConfirmDialog(this, panel, "Edit Job " + jobId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                String newName = nameField.getText().trim(); 
                String newStatus = (String) statusCombo.getSelectedItem();
                if (!newName.isEmpty()) {
                    jobToEdit.setJobName(newName); 
                    jobToEdit.setStatus(newStatus);
                    if (jobDAO.updateJob(jobToEdit)) { 
                        JOptionPane.showMessageDialog(this, "Job updated.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                        refreshAllData(); 
                    }
                    else { 
                        JOptionPane.showMessageDialog(this, "Failed to update job.", "Error", JOptionPane.ERROR_MESSAGE); 
                    }
                } else { 
                    JOptionPane.showMessageDialog(this, "Job Name cannot be empty.", "Input Error", JOptionPane.ERROR_MESSAGE); 
                }
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select a job to edit.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }

    private void deleteSelectedJob() {
        int selectedRow = jobTable.getSelectedRow();
        if (selectedRow != -1) {
            String jobId = (String) jobTableModel.getValueAt(selectedRow, 0);
            String status = (String) jobTableModel.getValueAt(selectedRow, 5);

            if (CloudControllerDAO.STATE_PENDING_APPROVAL.equals(status)) {
                JOptionPane.showMessageDialog(this, "Cannot delete pending job. Reject it instead.", "Action Not Allowed", JOptionPane.WARNING_MESSAGE); 
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(this, "Delete Job ID '" + jobId + "'?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (jobDAO.deleteJob(jobId)) {
                    JOptionPane.showMessageDialog(this, "Job '" + jobId + "' deleted.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    refreshAllData();
                } else { 
                    JOptionPane.showMessageDialog(this, "Failed to delete job '" + jobId + "'.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select a job to delete.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }

    private void addNewUser() {
        JTextField nameField = new JTextField(20); 
        JTextField emailField = new JTextField(20); 
        JPasswordField passField = new JPasswordField(20);
        String[] roleOptions = {"vehicle_owner,job_owner", "vehicle_owner", "job_owner", "cloud_controller"};
        JComboBox<String> rolesCombo = new JComboBox<>(roleOptions);
        
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        panel.add(new JLabel("Full Name:")); panel.add(nameField); 
        panel.add(new JLabel("Email:")); panel.add(emailField); 
        panel.add(new JLabel("Password:")); panel.add(passField);
        panel.add(new JLabel("Roles:")); panel.add(rolesCombo);
        
        int result = JOptionPane.showConfirmDialog(this, panel, "Add New User", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String fullName = nameField.getText().trim(); 
            String email = emailField.getText().trim(); 
            String password = new String(passField.getPassword());
            String roles = (String)rolesCombo.getSelectedItem();
            
            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || !email.contains("@")) {
                JOptionPane.showMessageDialog(this, "Valid Name, Email, and Password required.", "Input Error", JOptionPane.ERROR_MESSAGE); 
                return;
            }
            User user = new User(fullName, email, roles, password);
            if (userDAO.addUser(user)) { 
                JOptionPane.showMessageDialog(this, "User added.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                loadUserData(); 
                loadAllocationDropdowns(); 
            }
            else { 
                JOptionPane.showMessageDialog(this, "Failed to add user (Email might exist).", "Error", JOptionPane.ERROR_MESSAGE); 
            }
        }
    }

    private void editSelectedUser() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow != -1) {
            int userId = (int) userTableModel.getValueAt(selectedRow, 0); 
            User userToEdit = userDAO.getUserById(userId);
            if (userToEdit == null) { 
                JOptionPane.showMessageDialog(this, "Could not find user details.", "Error", JOptionPane.ERROR_MESSAGE); 
                return; 
            }
            JTextField nameField = new JTextField(userToEdit.getFullName(), 20); 
            JTextField emailField = new JTextField(userToEdit.getEmail(), 20);
            
            String[] roleOptions = {"vehicle_owner,job_owner", "vehicle_owner", "job_owner", "cloud_controller"};
            JComboBox<String> rolesCombo = new JComboBox<>(roleOptions);
            rolesCombo.setSelectedItem(userToEdit.getRolesAsString());
            
            JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5)); 
            panel.add(new JLabel("Full Name:")); panel.add(nameField); 
            panel.add(new JLabel("Email:")); panel.add(emailField);
            panel.add(new JLabel("Roles:")); panel.add(rolesCombo);
            
            int result = JOptionPane.showConfirmDialog(this, panel, "Edit User " + userId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                String newName = nameField.getText().trim(); 
                String newEmail = emailField.getText().trim();
                String newRoles = (String)rolesCombo.getSelectedItem();
                
                if (newName.isEmpty() || newEmail.isEmpty() || !newEmail.contains("@")) { 
                    JOptionPane.showMessageDialog(this, "Valid Name and Email required.", "Input Error", JOptionPane.ERROR_MESSAGE); 
                    return; 
                }
                userToEdit.setFullName(newName); 
                userToEdit.setEmail(newEmail);
                userToEdit.setRoles(newRoles);
                
                if (userDAO.updateUser(userToEdit)) { 
                    JOptionPane.showMessageDialog(this, "User updated.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                    loadUserData(); 
                    loadAllocationDropdowns(); 
                }
                else { 
                    JOptionPane.showMessageDialog(this, "Failed to update user.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select a user to edit.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }

    private void deleteSelectedUser() {
        int selectedRow = userTable.getSelectedRow();
        if (selectedRow != -1) {
            int userId = (int) userTableModel.getValueAt(selectedRow, 0); 
            String userName = (String) userTableModel.getValueAt(selectedRow, 1);
            int confirm = JOptionPane.showConfirmDialog(this, "Delete user '" + userName + "' (ID: " + userId + ") and ALL associated data?", "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                // Simple cascade delete simulation
                allocationDAO.getAllAllocations().stream()
                    .filter(a -> String.valueOf(userId).equals(a.getUserId()))
                    .forEach(a -> allocationDAO.deleteAllocation(a.getAllocationId()));
                
                jobDAO.getAllJobs().stream()
                    .filter(j -> j.getJobOwnerId() == userId)
                    .forEach(j -> jobDAO.deleteJob(j.getJobId()));
                
                VehicleDAO tempVehicleDAO = new VehicleDAO(); 
                tempVehicleDAO.getVehiclesByOwner(userId)
                    .forEach(v -> tempVehicleDAO.deleteVehicle(v.getVin()));
                
                if (userDAO.deleteUser(String.valueOf(userId))) { 
                    JOptionPane.showMessageDialog(this, "User '" + userName + "' deleted.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                    refreshAllData(); 
                }
                else { 
                    JOptionPane.showMessageDialog(this, "Failed to delete user '" + userName + "'.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select a user to delete.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }

    private void allocateUserToJob() {
        String userSelection = (String) userDropdown.getSelectedItem(); 
        String jobSelection = (String) jobDropdown.getSelectedItem();
        if (userSelection != null && jobSelection != null) {
            try {
                String userId = userSelection.split(" - ")[0]; 
                String jobId = jobSelection.split(" - ")[0];
                
                // Check if allocation already exists
                boolean exists = allocationDAO.getAllAllocations().stream()
                    .anyMatch(a -> a.getUserId().equals(userId) && a.getJobId().equals(jobId));
                    
                if (exists) { 
                    JOptionPane.showMessageDialog(this, "Allocation already exists.", "Allocation Exists", JOptionPane.INFORMATION_MESSAGE); 
                    return; 
                }
                
                Allocation allocation = new Allocation(userId, jobId);
                if (allocationDAO.addAllocation(allocation)) { 
                    JOptionPane.showMessageDialog(this, "User allocated to job successfully.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                    loadAllocationData(); 
                }
                else { 
                    JOptionPane.showMessageDialog(this, "Failed to create allocation.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            } catch (Exception ex) { 
                JOptionPane.showMessageDialog(this, "Error parsing selection: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select both user and job.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }

    private void removeSelectedAllocation() {
        int selectedRow = allocationTable.getSelectedRow();
        if (selectedRow != -1) {
            int allocationId = (int) allocationTableModel.getValueAt(selectedRow, 0);
            String userId = (String) allocationTableModel.getValueAt(selectedRow, 1);
            String jobId = (String) allocationTableModel.getValueAt(selectedRow, 2);
            
            int confirm = JOptionPane.showConfirmDialog(this, 
                "Remove allocation ID " + allocationId + " (User: " + userId + ", Job: " + jobId + ")?", 
                "Confirm Removal", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                
            if (confirm == JOptionPane.YES_OPTION) {
                if (allocationDAO.deleteAllocation(allocationId)) { 
                    JOptionPane.showMessageDialog(this, "Allocation removed successfully.", "Success", JOptionPane.INFORMATION_MESSAGE); 
                    loadAllocationData(); 
                }
                else { 
                    JOptionPane.showMessageDialog(this, "Failed to remove allocation.", "Error", JOptionPane.ERROR_MESSAGE); 
                }
            }
        } else { 
            JOptionPane.showMessageDialog(this, "Please select an allocation to remove.", "Selection Required", JOptionPane.WARNING_MESSAGE); 
        }
    }
    // --- Helper Methods ---

    private void setupTableAppearance(JTable table) {
        // Your existing code...
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(25);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.setBackground(new Color(210, 210, 210));
        header.setForeground(Color.BLACK);
        table.setSelectionBackground(new Color(184, 207, 229));
        table.setSelectionForeground(Color.BLACK);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
        ((DefaultTableCellRenderer)header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);
    }
}