package gui.client;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import dao.CloudControllerDAO;
import dao.JobDAO;
import models.Job;
import models.User;

public class ClientDashboard extends JPanel {
    private static final Logger logger = Logger.getLogger(ClientDashboard.class.getName());

    private User client; // Authenticated client (job owner)
    private JobDAO jobDAO = new JobDAO();
    private CloudControllerDAO cloudControllerDAO = new CloudControllerDAO();

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
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top navigation with title - CONSISTENT WITH OwnerDashboard
        JPanel topNav = new JPanel(new BorderLayout());
        topNav.setBackground(new Color(43, 43, 43));
        JLabel titleLabel = new JLabel("Job Owner Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        topNav.add(titleLabel, BorderLayout.CENTER);
        add(topNav, BorderLayout.NORTH);

        // Table setup
        String[] columnNames = {"Job ID", "Status", "Duration", "Deadline", "Created At", "Est. Completion"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        jobTable = new JTable(tableModel);
        jobTable.setBackground(new Color(230, 230, 230)); // CONSISTENT WITH OwnerDashboard
        jobTable.setForeground(Color.BLACK);
        jobTable.setRowHeight(30);
        jobTable.setFont(new Font("Arial", Font.PLAIN, 14));

        // Center-align all columns - CONSISTENT WITH OwnerDashboard
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < jobTable.getColumnCount(); i++) {
            jobTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JTableHeader header = jobTable.getTableHeader();
        header.setBackground(new Color(200, 200, 200));
        header.setForeground(Color.BLACK);
        header.setFont(new Font("Arial", Font.BOLD, 15));

        // Center table header text - CONSISTENT WITH OwnerDashboard
        ((DefaultTableCellRenderer)header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

        JScrollPane scrollPane = new JScrollPane(jobTable);
        add(scrollPane, BorderLayout.CENTER);

        // Filter, Refresh & Add Job Panel - CONSISTENT WITH OwnerDashboard
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.setBackground(new Color(43, 43, 43));
        
        JLabel statusLabel = new JLabel("Filter by Status:");
        statusLabel.setForeground(Color.WHITE);
        controlPanel.add(statusLabel);

        String[] statuses = {"All", CloudControllerDAO.STATE_PENDING_APPROVAL, CloudControllerDAO.STATE_QUEUED, CloudControllerDAO.STATE_PROGRESS, CloudControllerDAO.STATE_COMPLETED};
        statusFilter = new JComboBox<>(statuses);
        statusFilter.setBackground(Color.WHITE);
        statusFilter.setFont(new Font("Arial", Font.PLAIN, 14));
        statusFilter.addActionListener(e -> updateTable());
        controlPanel.add(statusFilter);

        refreshButton = new JButton("Refresh List");
        refreshButton.addActionListener(e -> updateTable());
        controlPanel.add(refreshButton);

        addJobButton = new JButton("Submit New Job");
        addJobButton.addActionListener(e -> openSubmitJobDialog());
        controlPanel.add(addJobButton);

        add(controlPanel, BorderLayout.SOUTH);

        // Initial data load
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
     * Opens a dialog for submitting a new job for approval - CONSISTENT STYLE WITH OwnerForm
     */
    private void openSubmitJobDialog() {
        // Use same layout approach as OwnerForm
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Use same styling as OwnerForm
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 14);

        gbc.gridx=0; gbc.gridy=0; 
        JLabel jobIdLabel = new JLabel("Job ID:", SwingConstants.LEFT);
        jobIdLabel.setFont(labelFont);
        panel.add(jobIdLabel, gbc);
        
        JTextField jobIdField = new JTextField(15);
        jobIdField.setFont(fieldFont);
        jobIdField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(jobIdField, gbc);
        
        gbc.gridx=0; gbc.gridy=1;
        JLabel jobNameLabel = new JLabel("Job Name:", SwingConstants.LEFT);
        jobNameLabel.setFont(labelFont);
        panel.add(jobNameLabel, gbc);
        
        JTextField jobNameField = new JTextField(15);
        jobNameField.setFont(fieldFont);
        jobNameField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(jobNameField, gbc);
        
        // Duration spinners with consistent styling
        gbc.gridx=0; gbc.gridy=2;
        JLabel durationLabel = new JLabel("Duration:", SwingConstants.LEFT);
        durationLabel.setFont(labelFont);
        panel.add(durationLabel, gbc);
        
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        durationPanel.setBackground(Color.WHITE);
        
        Dimension spinnerSize = new Dimension(50, 25);
        
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(0, 0, 99, 1);
        JSpinner hoursSpinner = new JSpinner(hoursModel);
        hoursSpinner.setEditor(new JSpinner.NumberEditor(hoursSpinner, "00"));
        hoursSpinner.setPreferredSize(spinnerSize);
        hoursSpinner.setFont(fieldFont);
        
        SpinnerNumberModel minutesModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner minutesSpinner = new JSpinner(minutesModel);
        minutesSpinner.setEditor(new JSpinner.NumberEditor(minutesSpinner, "00"));
        minutesSpinner.setPreferredSize(spinnerSize);
        minutesSpinner.setFont(fieldFont);
        
        SpinnerNumberModel secondsModel = new SpinnerNumberModel(0, 0, 59, 1);
        JSpinner secondsSpinner = new JSpinner(secondsModel);
        secondsSpinner.setEditor(new JSpinner.NumberEditor(secondsSpinner, "00"));
        secondsSpinner.setPreferredSize(spinnerSize);
        secondsSpinner.setFont(fieldFont);
        
        durationPanel.add(hoursSpinner); durationPanel.add(new JLabel("h"));
        durationPanel.add(Box.createHorizontalStrut(5));
        durationPanel.add(minutesSpinner); durationPanel.add(new JLabel("m"));
        durationPanel.add(Box.createHorizontalStrut(5));
        durationPanel.add(secondsSpinner); durationPanel.add(new JLabel("s"));
        
        gbc.gridx=1; panel.add(durationPanel, gbc);
        
        // Deadline spinner
        gbc.gridx=0; gbc.gridy=3;
        JLabel deadlineLabel = new JLabel("Deadline:", SwingConstants.LEFT);
        deadlineLabel.setFont(labelFont);
        panel.add(deadlineLabel, gbc);
        
        SpinnerDateModel dateModel = new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH);
        JSpinner dateSpinner = new JSpinner(dateModel);
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");
        dateSpinner.setEditor(dateEditor);
        dateSpinner.setFont(fieldFont);
        gbc.gridx=1; panel.add(dateSpinner, gbc);

        gbc.gridx=0; gbc.gridy=4; gbc.gridwidth=2; gbc.anchor = GridBagConstraints.CENTER;
        JLabel noteLabel = new JLabel("Job will be submitted for Controller approval.");
        noteLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        noteLabel.setForeground(Color.DARK_GRAY);
        panel.add(noteLabel, gbc);

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

            // Use socket communication
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
                    updateTable();
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
                
                connectToServer();
            }
        }
    }

    /**
     * Updates the job table based on the selected status filter.
     */
    public void updateTable() {
        try {
            tableModel.setRowCount(0);
            String selectedStatus = (String) statusFilter.getSelectedItem();
            Map<String, String> completionTimes = cloudControllerDAO.loadSchedule();
            Map<String, String> currentStates = cloudControllerDAO.loadJobStates();

            List<Job> allClientJobs = jobDAO.getJobsByClient(client.getUserId(), "All");

            for (Job job : allClientJobs) {
                String displayStatus = CloudControllerDAO.STATE_PENDING_APPROVAL.equals(job.getStatus())
                                     ? CloudControllerDAO.STATE_PENDING_APPROVAL
                                     : currentStates.getOrDefault(job.getJobId(), job.getStatus());

                if ("All".equalsIgnoreCase(selectedStatus) || selectedStatus.equalsIgnoreCase(displayStatus)) {
                    String estimatedCompletion = completionTimes.getOrDefault(job.getJobId(), "-");
                    if (CloudControllerDAO.STATE_PENDING_APPROVAL.equals(displayStatus)) {
                        estimatedCompletion = "N/A (Pending)";
                    } else if (CloudControllerDAO.STATE_COMPLETED.equals(displayStatus) && "-".equals(estimatedCompletion)) {
                        estimatedCompletion = "Completed";
                    }

                    tableModel.addRow(new Object[]{
                            job.getJobId(),
                            displayStatus,
                            job.getDuration(),
                            job.getDeadline(),
                            job.getCreatedTimestamp(),
                            estimatedCompletion
                    });
                }
            }
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error updating job table: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error loading job data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}