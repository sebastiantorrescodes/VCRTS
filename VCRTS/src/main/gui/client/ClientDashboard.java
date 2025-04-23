package gui.client;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import dao.CloudControllerDAO;
import dao.JobDAO;
import models.Job;
import models.User;

public class ClientDashboard extends JPanel {
    private static final Logger logger = Logger.getLogger(ClientDashboard.class.getName());

    private User client; // Authenticated client (job owner)
    private JobDAO jobDAO = new JobDAO();
    private CloudControllerDAO cloudControllerDAO = new CloudControllerDAO(); // For loading job status

    private JTable jobTable;
    private DefaultTableModel tableModel;
    private JComboBox<String> statusFilter;
    private JButton refreshButton, addJobButton;
    
    // Socket connection fields
    private Socket socket;
    private PrintWriter out; 
    private BufferedReader in;
    private final String SERVER_ADDRESS = "localhost";
    private final int SERVER_PORT = 9876; 
    private boolean connected = false;

    public ClientDashboard(User client) {
        this.client = client;
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE); // Cleaner background
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top panel with title
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(43, 43, 43));
        JLabel titleLabel = new JLabel("My Jobs Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(topPanel, BorderLayout.NORTH);

        // Table setup
        // Added "Est. Completion" from controller, removed "Time to Complete" (relative)
        String[] columnNames = {"Job ID", "Status", "Duration", "Deadline", "Created At", "Est. Completion"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        jobTable = new JTable(tableModel);
        setupTableAppearance(jobTable); // Use helper method for consistent look

        JScrollPane scrollPane = new JScrollPane(jobTable);
        add(scrollPane, BorderLayout.CENTER);

        // Filter, Refresh & Add Job Panel
        JPanel controlPanel = new JPanel();
        controlPanel.setBackground(Color.WHITE);
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));

        JLabel statusLabel = new JLabel("Filter by Status:");
        controlPanel.add(statusLabel);

        // Include "Pending Approval" in filter options
        String[] statuses = {"All", CloudControllerDAO.STATE_PENDING_APPROVAL, CloudControllerDAO.STATE_QUEUED, CloudControllerDAO.STATE_PROGRESS, CloudControllerDAO.STATE_COMPLETED};
        statusFilter = new JComboBox<>(statuses);
        statusFilter.setBackground(Color.WHITE);
        statusFilter.setFont(new Font("Arial", Font.PLAIN, 14));
        statusFilter.addActionListener(e -> updateTable());
        controlPanel.add(statusFilter);

        refreshButton = new JButton("Refresh List");
        refreshButton.setFont(new Font("Arial", Font.BOLD, 14));
        refreshButton.addActionListener(e -> updateTable());
        controlPanel.add(refreshButton);

        addJobButton = new JButton("Submit New Job"); // Changed text
        addJobButton.setFont(new Font("Arial", Font.BOLD, 14));
        addJobButton.addActionListener(e -> openSubmitJobDialog()); // Changed method called
        controlPanel.add(addJobButton);

        add(controlPanel, BorderLayout.SOUTH);

        // Initial data load and timer (optional, depends if real-time updates are desired)
        // new Timer(15000, e -> updateTable()).start(); // Slightly longer interval
        updateTable();
        
        // Connect to the server
        connectToServer();
        
        // Add shutdown hook to close connection when application exits
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnectFromServer));
    }
    
    // Socket connection methods
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                connected = true;
                System.out.println("Connected to Cloud Controller server");
                
                // Start a thread to listen for server messages
                new Thread(this::listenForServerMessages).start();
            } catch (IOException e) {
                System.err.println("Could not connect to Cloud Controller: " + e.getMessage());
                connected = false;
                
                // Schedule reconnect attempt
                Timer reconnectTimer = new Timer(5000, event -> connectToServer());
                reconnectTimer.setRepeats(false);
                reconnectTimer.start();
            }
        }).start();
    }
    
    private void listenForServerMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> processServerMessage(finalMessage));
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Error reading from server: " + e.getMessage());
                connected = false;
                
                // Try to reconnect
                connectToServer();
            }
        }
    }
    
    private void processServerMessage(String message) {
        System.out.println("Received from server: " + message);
        
        if (message.startsWith("JOB_APPROVAL_STATUS:")) {
            // Parse: JOB_APPROVAL_STATUS:jobId,approved/rejected
            String[] parts = message.substring("JOB_APPROVAL_STATUS:".length()).split(",");
            if (parts.length >= 2) {
                String jobId = parts[0];
                boolean approved = "approved".equals(parts[1]);
                
                // Show notification to user
                JOptionPane.showMessageDialog(this, 
                    "Your job (ID: " + jobId + ") has been " + (approved ? "approved" : "rejected") + 
                    "\n" + (approved ? "The job has been added to the system." : "Please check your submission details and try again."),
                    approved ? "Job Approved" : "Job Rejected", 
                    approved ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                
                // Refresh the job table
                updateTable();
            }
        }
    }
    
    private void disconnectFromServer() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            System.out.println("Disconnected from server");
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Opens a dialog for submitting a new job for approval.
     */
    private void openSubmitJobDialog() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.anchor = GridBagConstraints.WEST;

        JTextField jobIdField = new JTextField(15);
        JTextField jobNameField = new JTextField(15);
        
        // Duration spinners
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(0, 0, 99, 1);
        JSpinner hoursSpinner = new JSpinner(hoursModel);
        SpinnerNumberModel minutesModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner minutesSpinner = new JSpinner(minutesModel);
        SpinnerNumberModel secondsModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner secondsSpinner = new JSpinner(secondsModel);
        
        // Deadline spinner
        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
        JSpinner dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);

        gbc.gridx=0; gbc.gridy=0; panel.add(new JLabel("Job ID:"), gbc);
        gbc.gridx=1; gbc.gridy=0; panel.add(jobIdField, gbc);
        
        gbc.gridx=0; gbc.gridy=1; panel.add(new JLabel("Job Name:"), gbc);
        gbc.gridx=1; gbc.gridy=1; panel.add(jobNameField, gbc);

        gbc.gridx=0; gbc.gridy=2; panel.add(new JLabel("Duration:"), gbc);
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        durationPanel.add(hoursSpinner); durationPanel.add(new JLabel("h"));
        durationPanel.add(minutesSpinner); durationPanel.add(new JLabel("m"));
        durationPanel.add(secondsSpinner); durationPanel.add(new JLabel("s"));
        gbc.gridx=1; gbc.gridy=2; panel.add(durationPanel, gbc);

        gbc.gridx=0; gbc.gridy=3; panel.add(new JLabel("Deadline:"), gbc);
        gbc.gridx=1; gbc.gridy=3; panel.add(dateSpinner, gbc);

        gbc.gridx=0; gbc.gridy=4; gbc.gridwidth=2; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(new JLabel("Job will be submitted for Controller approval.", SwingConstants.CENTER), gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Submit New Job for Approval", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String jobId = jobIdField.getText().trim();
            String jobName = jobNameField.getText().trim();
            if (jobName.isEmpty()) {
                jobName = jobId; // Use ID as name if not provided
            }
            
            int hours = (Integer) hoursSpinner.getValue();
            int minutes = (Integer) minutesSpinner.getValue();
            int seconds = (Integer) secondsSpinner.getValue();
            String duration = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            
            Date selectedDate = (Date) dateSpinner.getValue();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String deadline = dateFormat.format(selectedDate);

            if (jobId.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Job ID is required!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (duration.equals("00:00:00")) {
                JOptionPane.showMessageDialog(this, "Duration must be greater than zero!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Use socket communication instead of directly calling the DAO
            if (connected && out != null) {
                try {
                    // Format: NEW_JOB:userId,jobId,jobName,duration,deadline
                    String message = String.format("NEW_JOB:%d,%s,%s,%s,%s", 
                        client.getUserId(), jobId, jobName, duration, deadline);
                    out.println(message);
                    System.out.println("Sent to server: " + message);
                    
                    JOptionPane.showMessageDialog(this,
                        "Job (ID: " + jobId + ") submitted for approval.\nWaiting for Cloud Controller to review.",
                        "Submission Success",
                        JOptionPane.INFORMATION_MESSAGE);
                    updateTable(); // Refresh table to show the new pending job
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, 
                        "Error sending job submission: " + e.getMessage(), 
                        "Connection Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Not connected to Cloud Controller server. Retrying connection...", 
                    "Connection Error", 
                    JOptionPane.ERROR_MESSAGE);
                
                // Try to reconnect
                connectToServer();
            }
        }
    }

    /**
     * Updates the job table based on the selected status filter.
     * Retrieves estimated completion times from the schedule file.
     */
    public void updateTable() {
        try {
            tableModel.setRowCount(0); // Clear table
            String selectedStatus = (String) statusFilter.getSelectedItem();
             Map<String, String> completionTimes = cloudControllerDAO.loadSchedule(); // Load schedule once
             Map<String, String> currentStates = cloudControllerDAO.loadJobStates(); // Load states once

            // Get all jobs *belonging to this client*
             // We need to handle filtering based on the *actual* status (from state file or pending)
            List<Job> allClientJobs = jobDAO.getJobsByClient(client.getUserId(), "All"); // Get all first

            for (Job job : allClientJobs) {
                 // Determine the correct status to display and filter by
                 String displayStatus = CloudControllerDAO.STATE_PENDING_APPROVAL.equals(job.getStatus())
                                      ? CloudControllerDAO.STATE_PENDING_APPROVAL
                                      : currentStates.getOrDefault(job.getJobId(), job.getStatus());

                 // Apply filter
                if ("All".equalsIgnoreCase(selectedStatus) || selectedStatus.equalsIgnoreCase(displayStatus)) {
                    String estimatedCompletion = completionTimes.getOrDefault(job.getJobId(), "-");
                     // Don't show completion time for pending jobs
                     if (CloudControllerDAO.STATE_PENDING_APPROVAL.equals(displayStatus)) {
                         estimatedCompletion = "N/A (Pending)";
                     } else if (CloudControllerDAO.STATE_COMPLETED.equals(displayStatus) && "-".equals(estimatedCompletion)) {
                          estimatedCompletion = "Completed"; // If state is complete but no time recorded
                     }

                    tableModel.addRow(new Object[]{
                            job.getJobId(),
                            displayStatus, // Show the accurate status
                            job.getDuration(),
                            job.getDeadline(),
                            job.getCreatedTimestamp(),
                            estimatedCompletion // Show estimated completion from schedule
                    });
                }
            }
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error updating job table: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error loading job data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

     // Helper method for consistent table appearance
    private void setupTableAppearance(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(28);
        table.setGridColor(Color.LIGHT_GRAY);
         table.setShowGrid(true);
         table.setIntercellSpacing(new Dimension(1,1));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(new Color(220, 220, 220));
         table.setSelectionBackground(new Color(184, 207, 229));
         table.setSelectionForeground(Color.BLACK);

        // Center-align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }
         // Center header text too
         ((DefaultTableCellRenderer)table.getTableHeader().getDefaultRenderer())
             .setHorizontalAlignment(JLabel.CENTER);
    }
}