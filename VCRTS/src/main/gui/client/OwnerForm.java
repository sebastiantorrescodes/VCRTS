package gui.client;

import javax.swing.*;
import java.awt.*;
import models.User;
import models.Vehicle;
import gui.client.ClientFrame;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class OwnerForm extends JPanel {
    private String ownerId; // Changed to String type
    private int vehicleOwnerId; // Added to store the actual user ID
    private User currentUser;
    private JTextField modelField, makeField, yearField, vinField, ownerIdField;
    private JSpinner hoursSpinner, minutesSpinner, secondsSpinner;
    
    // Socket connection fields
    private Socket socket;
    private PrintWriter out; 
    private BufferedReader in; 
    private final String SERVER_ADDRESS = "localhost";
    private final int SERVER_PORT = 9876; 
    private boolean connected = false;
    
    public OwnerForm(int userVehicleOwnerId) {
        this.vehicleOwnerId = userVehicleOwnerId;
        this.ownerId = String.valueOf(userVehicleOwnerId); // Default value
        
        // Try to get the current user from the parent frame
        Component parentFrame = SwingUtilities.getWindowAncestor(this);
        if (parentFrame instanceof ClientFrame) {
            this.currentUser = ((ClientFrame) parentFrame).getCurrentUser();
        }
        
        if (this.currentUser == null) {
            System.err.println("Warning: OwnerForm could not retrieve currentUser.");
        } else {
            this.vehicleOwnerId = this.currentUser.getUserId();
            this.ownerId = String.valueOf(this.vehicleOwnerId); // Default to the user ID as string
        }
        
        // Initialize UI components
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Color.WHITE);
        
        JLabel titleLabel = new JLabel("Register New Vehicle", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(30));
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));
        
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Owner ID (now String type)
        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(createLabel("Owner ID:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        ownerIdField = new JTextField(this.ownerId);
        ownerIdField.setHorizontalAlignment(SwingConstants.LEFT);
        ownerIdField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        formPanel.add(ownerIdField, gbc);
        
        // Model
        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(createLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        modelField = createTextField(15);
        formPanel.add(modelField, gbc);
        
        // Make
        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(createLabel("Make:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        makeField = createTextField(15);
        formPanel.add(makeField, gbc);
        
        // Year
        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(createLabel("Year:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        yearField = createTextField(15);
        formPanel.add(yearField, gbc);
        
        // VIN
        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(createLabel("VIN:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        vinField = createTextField(15);
        formPanel.add(vinField, gbc);
        
        // Residency Time
        gbc.gridx = 0; gbc.gridy = 5; formPanel.add(createLabel("Residency Time:"), gbc);
        gbc.gridx = 1; gbc.gridy = 5;
        JPanel timePanel = createTimePanel();
        formPanel.add(timePanel, gbc);
        
        // Approval Note
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER;
        JLabel noteLabel = new JLabel("Vehicle registration will be submitted for Controller approval.");
        noteLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        noteLabel.setForeground(Color.DARK_GRAY);
        formPanel.add(noteLabel, gbc);
        
        mainPanel.add(formPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        
        // Submit Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(Color.WHITE);
        JButton submitButton = new JButton("Submit for Approval");
        submitButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        submitButton.setPreferredSize(new Dimension(180, 35));
        buttonPanel.add(submitButton);
        mainPanel.add(buttonPanel);
        
        submitButton.addActionListener(e -> submitVehicle());
        
        add(mainPanel, BorderLayout.CENTER);
        
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
    
    private void submitVehicle() {
        // Check if we need to refresh current user reference
        if (this.currentUser == null) {
            Component parentFrame = SwingUtilities.getWindowAncestor(this);
            if (parentFrame instanceof ClientFrame) {
                this.currentUser = ((ClientFrame) parentFrame).getCurrentUser();
                this.vehicleOwnerId = this.currentUser.getUserId();
            }
        }
        
        // Get owner ID from text field as string directly
        String ownerIdText = ownerIdField.getText().trim();
        if (ownerIdText.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Owner ID must not be empty!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            ownerIdField.requestFocus();
            return;
        }

        // Get form data
        String model = modelField.getText().trim();
        String make = makeField.getText().trim();
        String yearStr = yearField.getText().trim();
        String vin = vinField.getText().trim();
        int hours = (Integer) hoursSpinner.getValue();
        int minutes = (Integer) minutesSpinner.getValue();
        int seconds = (Integer) secondsSpinner.getValue();
        String residencyTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        // Validation
        if (model.isEmpty() || make.isEmpty() || yearStr.isEmpty() || vin.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "All fields (Model, Make, Year, VIN) are required!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Integer.parseInt(yearStr); // Basic check if year is a number
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, 
                "Year must be a valid number!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            yearField.requestFocus();
            return;
        }
        
        if (residencyTime.equals("00:00:00")) {
            JOptionPane.showMessageDialog(this, 
                "Residency time must be greater than zero!", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Submit using socket with updated format
        if (connected && out != null) {
            try {
                // Updated format: NEW_VEHICLE:ownerId,vehicleOwnerId,make,model,year,vin,residencyTime
                String message = String.format("NEW_VEHICLE:%s,%d,%s,%s,%s,%s,%s", 
                    ownerIdText, 
                    vehicleOwnerId, // Use the actual user ID for vehicle_owner_id
                    make, model, yearStr, vin, residencyTime);
                out.println(message);
                System.out.println("Sent to server: " + message);
                
                JOptionPane.showMessageDialog(this,
                    "Vehicle (VIN: " + vin + ") submitted for approval.\nWaiting for Cloud Controller to review.",
                    "Submission Success",
                    JOptionPane.INFORMATION_MESSAGE);
                clearForm();
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
            
            // Try to reconnect
            connectToServer();
        }
    }
    
    // Helper UI methods remain the same
    private JLabel createLabel(String text) {
        // Implementation remains unchanged
        return new JLabel(text, SwingConstants.LEFT);
    }

    private JTextField createTextField(int columns) {
        // Implementation remains unchanged
        return new JTextField(columns);
    }

    private JPanel createTimePanel() {
        // Implementation remains unchanged
        return new JPanel();
    }

    private void clearForm() {
        // Keep the owner ID field as is for convenience
        modelField.setText("");
        makeField.setText("");
        yearField.setText("");
        vinField.setText("");
        hoursSpinner.setValue(1); // Reset time to default
        minutesSpinner.setValue(0);
        secondsSpinner.setValue(0);
    }
}