package gui.client;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import dao.VehicleDAO;
import models.User;
import models.Vehicle;

public class OwnerDashboard extends JPanel {
    private static final Logger logger = Logger.getLogger(OwnerDashboard.class.getName());

    private int ownerId;
    private VehicleDAO vehicleDAO = new VehicleDAO();
    private User currentUser;

    // Components for the vehicle list view
    private JTable vehicleTable;
    private DefaultTableModel tableModel;
    
    // Socket connection fields
    private Socket socket;
    private PrintWriter out; 
    private BufferedReader in;
    private final String SERVER_ADDRESS = "localhost";
    private final int SERVER_PORT = 9876; 
    private boolean connected = false;

    public OwnerDashboard(int ownerId) {
        this.ownerId = ownerId;
        
        // Match Job Owner dashboard style
        setLayout(new BorderLayout(10, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Title label with darker background - match Job Owner style
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(33, 33, 33));
        
        JLabel titleLabel = new JLabel("Vehicle Owner Dashboard", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        headerPanel.add(titleLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);
        
        // Create vehicle list panel
        add(createVehicleListPanel(), BorderLayout.CENTER);
        
        // Try to get current user
        Component parentFrame = SwingUtilities.getWindowAncestor(this);
        if (parentFrame instanceof ClientFrame) {
            this.currentUser = ((ClientFrame) parentFrame).getCurrentUser();
        }
        
        // Connect to the server
        connectToServer();
        
        // Add shutdown hook to close connection when application exits
        Runtime.getRuntime().addShutdownHook(new Thread(this::disconnectFromServer));
    }

    private JPanel createVehicleListPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Table setup
        String[] columnNames = {"Owner ID", "Model", "Make", "Year", "VIN", "Residency Time", "Registered At"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        vehicleTable = new JTable(tableModel);
        vehicleTable.setBackground(new Color(230, 230, 230));
        vehicleTable.setForeground(Color.BLACK);
        vehicleTable.setRowHeight(30);
        vehicleTable.setFont(new Font("Arial", Font.PLAIN, 14));
        vehicleTable.setGridColor(Color.GRAY);
        vehicleTable.setShowGrid(true);

        // Center-align all columns
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        for (int i = 0; i < vehicleTable.getColumnCount(); i++) {
            vehicleTable.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JTableHeader header = vehicleTable.getTableHeader();
        header.setBackground(new Color(200, 200, 200));
        header.setForeground(Color.BLACK);
        header.setFont(new Font("Arial", Font.BOLD, 15));
        header.setReorderingAllowed(false);

        // Center table header text
        ((DefaultTableCellRenderer)header.getDefaultRenderer()).setHorizontalAlignment(JLabel.CENTER);

        JScrollPane scrollPane = new JScrollPane(vehicleTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.add(scrollPane, BorderLayout.CENTER);

        // Control panel at the bottom with filter, refresh and submit buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.setBackground(new Color(33, 33, 33));
        
        // Filter dropdown - matches Job Owner dashboard
        JLabel filterLabel = new JLabel("Filter by Year:");
        filterLabel.setForeground(Color.WHITE);
        JComboBox<String> yearFilter = new JComboBox<>(new String[]{"All"});
        yearFilter.setPreferredSize(new Dimension(150, 25));
        
        JButton refreshButton = new JButton("Refresh List");
        JButton submitVehicleButton = new JButton("Submit New Vehicle");
        
        refreshButton.addActionListener(e -> refreshVehicleTable());
        submitVehicleButton.addActionListener(e -> openSubmitVehicleDialog());
        
        controlPanel.add(filterLabel);
        controlPanel.add(yearFilter);
        controlPanel.add(refreshButton);
        controlPanel.add(submitVehicleButton);
        panel.add(controlPanel, BorderLayout.SOUTH);

        // Initial data load
        refreshVehicleTable();
        return panel;
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
        
        if (message.startsWith("APPROVAL_STATUS:")) {
            // Parse: APPROVAL_STATUS:vin,approved/rejected
            String[] parts = message.substring("APPROVAL_STATUS:".length()).split(",");
            if (parts.length >= 2) {
                String vin = parts[0];
                boolean approved = "approved".equals(parts[1]);
                
                // Show notification to user
                JOptionPane.showMessageDialog(this, 
                    "Your vehicle (VIN: " + vin + ") has been " + (approved ? "approved" : "rejected") + 
                    "\n" + (approved ? "The vehicle has been added to the system." : "Please check your submission details and try again."),
                    approved ? "Registration Approved" : "Registration Rejected", 
                    approved ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                
                // Refresh the vehicle table
                refreshVehicleTable();
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
     * Opens a dialog for submitting a new vehicle
     */
    private void openSubmitVehicleDialog() {
        // Update current user reference if needed
        if (this.currentUser == null) {
            Component parentFrame = SwingUtilities.getWindowAncestor(this);
            if (parentFrame instanceof ClientFrame) {
                this.currentUser = ((ClientFrame) parentFrame).getCurrentUser();
            }
        }
        
        // Use same layout approach as job submission dialog
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // Use similar styling as job form
        Font labelFont = new Font("Segoe UI", Font.PLAIN, 14);
        Font fieldFont = new Font("Segoe UI", Font.PLAIN, 14);

        // Owner ID field (read-only, set to current user)
        gbc.gridx=0; gbc.gridy=0; 
        JLabel ownerIdLabel = new JLabel("Owner ID:", SwingConstants.LEFT);
        ownerIdLabel.setFont(labelFont);
        panel.add(ownerIdLabel, gbc);
        
        JTextField ownerIdField = new JTextField(15);
        ownerIdField.setFont(fieldFont);
        ownerIdField.setHorizontalAlignment(SwingConstants.LEFT);
        ownerIdField.setText(String.valueOf(ownerId));
        ownerIdField.setEditable(false);
        gbc.gridx=1; panel.add(ownerIdField, gbc);
        
        // Model field
        gbc.gridx=0; gbc.gridy=1;
        JLabel modelLabel = new JLabel("Model:", SwingConstants.LEFT);
        modelLabel.setFont(labelFont);
        panel.add(modelLabel, gbc);
        
        JTextField modelField = new JTextField(15);
        modelField.setFont(fieldFont);
        modelField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(modelField, gbc);
        
        // Make field
        gbc.gridx=0; gbc.gridy=2;
        JLabel makeLabel = new JLabel("Make:", SwingConstants.LEFT);
        makeLabel.setFont(labelFont);
        panel.add(makeLabel, gbc);
        
        JTextField makeField = new JTextField(15);
        makeField.setFont(fieldFont);
        makeField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(makeField, gbc);
        
        // Year field
        gbc.gridx=0; gbc.gridy=3;
        JLabel yearLabel = new JLabel("Year:", SwingConstants.LEFT);
        yearLabel.setFont(labelFont);
        panel.add(yearLabel, gbc);
        
        JTextField yearField = new JTextField(15);
        yearField.setFont(fieldFont);
        yearField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(yearField, gbc);
        
        // VIN field
        gbc.gridx=0; gbc.gridy=4;
        JLabel vinLabel = new JLabel("VIN:", SwingConstants.LEFT);
        vinLabel.setFont(labelFont);
        panel.add(vinLabel, gbc);
        
        JTextField vinField = new JTextField(15);
        vinField.setFont(fieldFont);
        vinField.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx=1; panel.add(vinField, gbc);
        
        // Residency Time spinners
        gbc.gridx=0; gbc.gridy=5;
        JLabel residencyLabel = new JLabel("Residency Time:", SwingConstants.LEFT);
        residencyLabel.setFont(labelFont);
        panel.add(residencyLabel, gbc);
        
        JPanel durationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        durationPanel.setBackground(Color.WHITE);
        
        Dimension spinnerSize = new Dimension(50, 25);
        
        SpinnerNumberModel hoursModel = new SpinnerNumberModel(1, 0, 99, 1);
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
        
        // Approval note
        gbc.gridx=0; gbc.gridy=6; gbc.gridwidth=2; gbc.anchor = GridBagConstraints.CENTER;
        JLabel noteLabel = new JLabel("Vehicle will be submitted for Controller approval.");
        noteLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        noteLabel.setForeground(Color.DARK_GRAY);
        panel.add(noteLabel, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Submit New Vehicle for Approval", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String model = modelField.getText().trim();
            String make = makeField.getText().trim();
            String year = yearField.getText().trim();
            String vin = vinField.getText().trim();
            
            int hours = (Integer) hoursSpinner.getValue();
            int minutes = (Integer) minutesSpinner.getValue();
            int seconds = (Integer) secondsSpinner.getValue();
            String residencyTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            if (model.isEmpty() || make.isEmpty() || year.isEmpty() || vin.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields (Model, Make, Year, VIN) are required!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // Basic validation for year
            try {
                int yearVal = Integer.parseInt(year);
                if (yearVal < 1900 || yearVal > 2025) {
                    JOptionPane.showMessageDialog(this, "Please enter a valid year between 1900 and 2025!", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Year must be a valid number!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            if (residencyTime.equals("00:00:00")) {
                JOptionPane.showMessageDialog(this, "Residency time must be greater than zero!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Use socket communication
            if (connected && out != null) {
                try {
                    // Format: NEW_VEHICLE:userId,make,model,year,vin,residencyTime
                    String message = String.format("NEW_VEHICLE:%d,%s,%s,%s,%s,%s", 
                        ownerId, make, model, year, vin, residencyTime);
                    out.println(message);
                    System.out.println("Sent to server: " + message);
                    
                    JOptionPane.showMessageDialog(this,
                        "Vehicle (VIN: " + vin + ") submitted for approval.\nWaiting for Cloud Controller to review.",
                        "Submission Success",
                        JOptionPane.INFORMATION_MESSAGE);
                    refreshVehicleTable();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, 
                        "Error sending vehicle registration: " + e.getMessage(), 
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

    public void refreshVehicleTable() {
        try {
            tableModel.setRowCount(0);
            List<Vehicle> vehicles = vehicleDAO.getVehiclesByOwner(ownerId);
            for (Vehicle v : vehicles) {
                tableModel.addRow(new Object[]{
                        v.getOwnerId(),
                        v.getModel(),
                        v.getMake(),
                        v.getYear(),
                        v.getVin(),
                        v.getResidencyTime(),
                        v.getRegisteredTimestamp()
                });
            }
        } catch(Exception ex) {
            logger.log(Level.SEVERE, "Error refreshing vehicle table: " + ex.getMessage(), ex);
            JOptionPane.showMessageDialog(this, "Error loading vehicle data: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}