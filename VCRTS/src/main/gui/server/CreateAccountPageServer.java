package gui.server;

import javax.swing.*;
import java.awt.*;
import dao.UserDAO;
import models.User;

public class CreateAccountPageServer  extends JPanel {
    private ServerFrame parent;
    private JTextField fullNameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton createButton;
    private JButton backButton;
    private JRadioButton vehicleOwnerRadio;
    private JRadioButton jobOwnerRadio;
    private ButtonGroup roleGroup;

    public CreateAccountPageServer(ServerFrame parent){
        this.parent = parent;
        setOpaque(true);
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);
        add(createFormPanel(), BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        JLabel title = new JLabel("Create a New Account", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        header.add(title);
        return header;
    }

    private JPanel createFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.CENTER;

        // Account type selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        JLabel roleLabel = new JLabel("Choose Account type: ", SwingConstants.CENTER);
        roleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(roleLabel, gbc);

        roleGroup = new ButtonGroup();
        
        vehicleOwnerRadio = new JRadioButton("Vehicle Owner");
        vehicleOwnerRadio.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        vehicleOwnerRadio.setSelected(true);
        vehicleOwnerRadio.setOpaque(false);
        
        jobOwnerRadio = new JRadioButton("Job Owner");
        jobOwnerRadio.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        jobOwnerRadio.setOpaque(false);

        roleGroup.add(vehicleOwnerRadio);
        roleGroup.add(jobOwnerRadio);

        JPanel radioPanel = new JPanel(new GridLayout(1, 2, 10, 5));
        radioPanel.setOpaque(false);
        radioPanel.add(vehicleOwnerRadio);
        radioPanel.add(jobOwnerRadio);
        
        gbc.gridx = 1;
        formPanel.add(radioPanel, gbc);

        // Full Name
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel fullNameLabel = new JLabel("Full Name:", SwingConstants.CENTER);
        fullNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(fullNameLabel, gbc);

        gbc.gridx = 1;
        fullNameField = new JTextField(15);
        fullNameField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        fullNameField.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(fullNameField, gbc);

        // Email - Fixed the grid position
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel emailLabel = new JLabel("Email:", SwingConstants.CENTER);
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(emailLabel, gbc);

        gbc.gridx = 1;
        emailField = new JTextField(15);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        emailField.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(emailField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 3;
        JLabel passwordLabel = new JLabel("Password: ", SwingConstants.CENTER);
        passwordLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(15);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passwordField.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(passwordField, gbc);

        // Notice about roles
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        JLabel rolesInfoLabel = new JLabel("Your account will have access to both Vehicle Owner and Job Owner functions.", SwingConstants.CENTER);
        rolesInfoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        formPanel.add(rolesInfoLabel, gbc);

        // Create Account Button
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        createButton = new JButton("Create Account");
        createButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        createButton.setPreferredSize(new Dimension(150, 35));
        formPanel.add(createButton, gbc);

        // Back Button
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        backButton = new JButton("Back");
        backButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        backButton.setPreferredSize(new Dimension(150, 35));
        formPanel.add(backButton, gbc);

        // Action Listener for Create Account Button
        createButton.addActionListener(e -> {
            String fullName = fullNameField.getText().trim();
                String email = emailField.getText().trim();
                String password = new String(passwordField.getPassword());

                // Validate that all fields are filled
                if (fullName.isEmpty() && email.isEmpty() && password.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        "Please fill in all fields (name, email, and password).", 
                        "Validation Error", 
                        JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                // Validate individual fields
                if (fullName.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        "Please enter your full name.", 
                        "Validation Error", 
                        JOptionPane.WARNING_MESSAGE);
                    fullNameField.requestFocus();
                    return;
                }
                
                if (email.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        "Please enter your email address.", 
                        "Validation Error", 
                        JOptionPane.WARNING_MESSAGE);
                    emailField.requestFocus();
                    return;
                }
                
                if (password.isEmpty()) {
                    JOptionPane.showMessageDialog(this, 
                        "Please enter a password.", 
                        "Validation Error", 
                        JOptionPane.WARNING_MESSAGE);
                    passwordField.requestFocus();
                    return;
                }

            // Automatically assign both vehicle_owner and job_owner roles
            String roles = "vehicle_owner,job_owner";

            UserDAO userDAO = new UserDAO();
            boolean success = userDAO.addUser(new User(fullName, email, roles, password));
            if (success) {
                JOptionPane.showMessageDialog(this, "Account created successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                parent.showPage("login");
            } else {
                JOptionPane.showMessageDialog(this, "Account creation failed. Please try again.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Action Listener for Back Button
        backButton.addActionListener(e -> parent.showPage("startup"));

        return formPanel;
}


}
