package gui.server;

import javax.swing.*;

import java.awt.*;
import java.util.logging.Logger;
import gui.server.LoginPageServer;
import dao.UserDAO;
import models.User;

public class LoginPageServer extends JPanel {
    private static final Logger logger = Logger.getLogger(LoginPageServer.class.getName());
    private final ServerFrame parent;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton backButton;
    private JRadioButton vehicleOwnerRadio;
    private JRadioButton jobOwnerRadio;
    private JRadioButton cloudControllerRadio;
    private ButtonGroup roleGroup;
    private UserDAO userDAO = new UserDAO();

    public LoginPageServer(ServerFrame parent) {
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
        JLabel title = new JLabel("Login", SwingConstants.CENTER);
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

        // Role Selection - Directly in the form panel
        gbc.gridx = 0;
        gbc.gridy = 0;
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
        
        cloudControllerRadio = new JRadioButton("Cloud Controller");
        cloudControllerRadio.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        cloudControllerRadio.setOpaque(false);
        
        roleGroup.add(vehicleOwnerRadio);
        roleGroup.add(jobOwnerRadio);
        roleGroup.add(cloudControllerRadio);
        
        JPanel radioPanel = new JPanel(new GridLayout(1, 3, 10, 5));
        radioPanel.setOpaque(false);
        radioPanel.add(vehicleOwnerRadio);
        radioPanel.add(jobOwnerRadio);
        radioPanel.add(cloudControllerRadio);
        
        gbc.gridx = 1;
        formPanel.add(radioPanel, gbc);
        
        // Email field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        JLabel emailLabel = new JLabel("Email:", SwingConstants.CENTER);
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(emailLabel, gbc);

        gbc.gridx = 1;
        emailField = new JTextField(15);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        emailField.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(emailField, gbc);

        // Password field
        gbc.gridx = 0;
        gbc.gridy = 2;
        JLabel passwordLabel = new JLabel("Password:", SwingConstants.CENTER);
        passwordLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        formPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        passwordField = new JPasswordField(15);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passwordField.setHorizontalAlignment(SwingConstants.CENTER);
        formPanel.add(passwordField, gbc);

        // Login button
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginButton = new JButton("Login");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        loginButton.setPreferredSize(new Dimension(120, 35));
        formPanel.add(loginButton, gbc);

        // Back button
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        backButton = new JButton("Back");
        backButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        backButton.setPreferredSize(new Dimension(120, 35));
        formPanel.add(backButton, gbc);

        loginButton.addActionListener(e -> {
            String email = emailField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (email.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Both email and password are required.", "Login Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            User user = userDAO.authenticate(email, password);
            if (user != null) {
                logger.info("User authenticated: " + user);

                String selectedRole = getSelectedRole();
                
                // Check if user is trying to access cloud controller role
                if (selectedRole.equals("cloud_controller") && !user.hasRole("cloud_controller")) {
                    JOptionPane.showMessageDialog(this, 
                        "You don't have permission to access the Cloud Controller role.", 
                        "Access Denied", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                // Ensure regular users have both regular roles
                if (!user.hasRole("vehicle_owner")) {
                    user.addRole("vehicle_owner");
                }
                if (!user.hasRole("job_owner")) {
                    user.addRole("job_owner");
                }

                // Set the selected role as current
                user.setCurrentRole(selectedRole);
                
                parent.showDashboard(user);
            } else {
                JOptionPane.showMessageDialog(this, "Invalid credentials. Please try again.", "Login Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        backButton.addActionListener(e -> parent.showPage("startup"));

        return formPanel;
    }
    
    private String getSelectedRole() {
        if (jobOwnerRadio.isSelected()) {
            return "job_owner";
        } else if (cloudControllerRadio.isSelected()) {
            return "cloud_controller";
        } else {
            return "vehicle_owner"; // Default
        }
    }
    
}