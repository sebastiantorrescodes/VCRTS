package gui.server; // Assuming this is the correct package

import javax.swing.*;
import java.awt.*;
import dao.VehicleDAO;
import dao.CloudControllerDAO; // Needed to submit for approval
import models.User; // Needed to identify the user submitting
import models.Vehicle;
import gui.server.ServerFrame; // To get current user
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class OwnerForm extends JPanel {
    private int ownerId;
    private User currentUser; // Store the logged-in user
    private JTextField modelField, makeField, yearField, vinField;
    private JSpinner hoursSpinner, minutesSpinner, secondsSpinner;
    // Removed direct VehicleDAO instance, use CloudControllerDAO for submission
    private CloudControllerDAO cloudControllerDAO = new CloudControllerDAO();
    private Socket socket;
    private PrintWriter out; 
    private BufferedReader in; 
    private final String SERVER_ADDRESS = "localhost";
    private final int SERVER_PORT = 9876; 
    private boolean connected = false; 
    
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                connected = true;
                
                // Start a thread to listen for server messages
                new Thread(this::listenForServerMessages).start();
            } catch (IOException e) {
                System.err.println("Could not connect to Cloud Controller: " + e.getMessage());
                connected = false;
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
            }
        }
    }
    
    private void processServerMessage(String message) {
        // Handle messages from the server
        if (message.startsWith("APPROVAL_STATUS:")) {
            // Example: APPROVAL_STATUS:vin,approved/rejected
            String[] parts = message.substring("APPROVAL_STATUS:".length()).split(",");
            String vin = parts[0];
            boolean approved = "approved".equals(parts[1]);
            
            // Show notification to user
            JOptionPane.showMessageDialog(this, 
                "Your vehicle (VIN: " + vin + ") has been " + (approved ? "approved" : "rejected"),
                "Approval Update", 
                approved ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void disconnectFromServer() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }
    
    
    

    public OwnerForm(int ownerId) {
        // It's better to pass the User object if possible, or retrieve it
        // For now, we'll try to get it from the parent ServerFrame if this panel is used within it.
         this.ownerId = ownerId; // Keep ownerId for display if needed
         // Attempt to get the currentUser from the ServerFrame parent
         Component parentFrame = SwingUtilities.getWindowAncestor(this);
         if (parentFrame instanceof ServerFrame) {
             this.currentUser = ((ServerFrame) parentFrame).getCurrentUser();
         }
         // If currentUser is still null, log a warning or handle appropriately
         if (this.currentUser == null) {
             System.err.println("Warning: OwnerForm could not retrieve currentUser.");
             // Fallback: Create a dummy user object if necessary, though this is not ideal
             // this.currentUser = new User("Unknown", "unknown@example.com", "vehicle_owner", "");
             // this.currentUser.setUserId(ownerId);
         } else {
              this.ownerId = this.currentUser.getUserId(); // Ensure ownerId matches logged-in user
         }


        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
         setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));


        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Color.WHITE);


        JLabel titleLabel = new JLabel("Register New Vehicle", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(Box.createVerticalStrut(30)); // Reduced top space
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(20));


        // Use GridBagLayout for the form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8); // Increased spacing
        gbc.anchor = GridBagConstraints.WEST;


        // Owner ID (Read-only)
        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(createLabel("Owner ID:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        JTextField ownerIdField = new JTextField(String.valueOf(this.ownerId));
        ownerIdField.setEditable(false);
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
        JPanel timePanel = createTimePanel(); // Use helper
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
    }


     // Helper to create styled labels
    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return label;
    }


    // Helper to create styled text fields
    private JTextField createTextField(int columns) {
        JTextField textField = new JTextField(columns);
        textField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textField.setHorizontalAlignment(SwingConstants.LEFT);
        return textField;
    }


    // Helper to create the time spinner panel
    private JPanel createTimePanel() {
         JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
         timePanel.setBackground(Color.WHITE);


        Dimension spinnerSize = new Dimension(50, 25);
        Font spinnerFont = new Font("Segoe UI", Font.PLAIN, 14);


        SpinnerNumberModel hoursModel = new SpinnerNumberModel(1, 0, 99, 1); // Allow > 23 hours
        hoursSpinner = new JSpinner(hoursModel);
        hoursSpinner.setEditor(new JSpinner.NumberEditor(hoursSpinner, "00"));
        hoursSpinner.setPreferredSize(spinnerSize);
         hoursSpinner.setFont(spinnerFont);


        SpinnerNumberModel minutesModel = new SpinnerNumberModel(0, 0, 59, 1);
        minutesSpinner = new JSpinner(minutesModel);
        minutesSpinner.setEditor(new JSpinner.NumberEditor(minutesSpinner, "00"));
        minutesSpinner.setPreferredSize(spinnerSize);
         minutesSpinner.setFont(spinnerFont);


        SpinnerNumberModel secondsModel = new SpinnerNumberModel(0, 0, 59, 1);
        secondsSpinner = new JSpinner(secondsModel);
        secondsSpinner.setEditor(new JSpinner.NumberEditor(secondsSpinner, "00"));
        secondsSpinner.setPreferredSize(spinnerSize);
         secondsSpinner.setFont(spinnerFont);


        timePanel.add(hoursSpinner); timePanel.add(new JLabel("h"));
        timePanel.add(Box.createHorizontalStrut(5));
        timePanel.add(minutesSpinner); timePanel.add(new JLabel("m"));
        timePanel.add(Box.createHorizontalStrut(5));
        timePanel.add(secondsSpinner); timePanel.add(new JLabel("s"));


         return timePanel;
    }


    private void submitVehicle() {
         // Retrieve currentUser again in case the panel was created before login completed
         if (this.currentUser == null) {
             Component parentFrame = SwingUtilities.getWindowAncestor(this);
             if (parentFrame instanceof ServerFrame) {
                 this.currentUser = ((ServerFrame) parentFrame).getCurrentUser();
             }
         }


         if (this.currentUser == null) {
             JOptionPane.showMessageDialog(this, "Cannot submit: User information not available.", "Error", JOptionPane.ERROR_MESSAGE);
             return;
         }


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
            JOptionPane.showMessageDialog(this, "All fields (Model, Make, Year, VIN) are required!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Integer.parseInt(yearStr); // Basic check if year is a number
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Year must be a valid number!", "Error", JOptionPane.ERROR_MESSAGE);
             yearField.requestFocus();
            return;
        }
        if (residencyTime.equals("00:00:00")) {
            JOptionPane.showMessageDialog(this, "Residency time must be greater than zero!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }


        // Create Vehicle object (timestamp set automatically in constructor)
        // Use the current logged-in user's ID
        Vehicle vehicle = new Vehicle(currentUser.getUserId(), model, make, yearStr, vin, residencyTime);


        // Submit using CloudControllerDAO
        boolean submitted = cloudControllerDAO.submitVehicleForApproval(vehicle, currentUser);


        if (submitted) {
            JOptionPane.showMessageDialog(this,
                    "Vehicle (VIN: " + vin + ") submitted successfully for approval.",
                    "Submission Success",
                    JOptionPane.INFORMATION_MESSAGE);
            clearForm(); // Clear fields after successful submission
        } else {
            JOptionPane.showMessageDialog(this, "Error submitting vehicle for approval.", "Submission Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void clearForm() {
        modelField.setText("");
        makeField.setText("");
        yearField.setText("");
        vinField.setText("");
        hoursSpinner.setValue(1); // Reset time to default
        minutesSpinner.setValue(0);
        secondsSpinner.setValue(0);
    }
}