package gui.client;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;

public class StartupPageClient extends JPanel {
    private final ClientFrame parent;
    private final String headerText;

    // Modified constructor to accept header text
    public StartupPageClient(ClientFrame parent, String headerText){
        this.parent = parent;
        this.headerText = headerText;
        setOpaque(true);
        setBackground(Color.WHITE);
        setLayout(new BorderLayout());
        add(createHeader(), BorderLayout.NORTH);
        add(createAuthenticationPanel(), BorderLayout.CENTER);
    }
    
    // For backward compatibility
    public StartupPageClient(ClientFrame parent) {
        this(parent, "Welcome to the VCRTS Client Interface");
    }

    private JPanel createHeader(){
        JPanel header = new JPanel(); 
        header.setOpaque(false);
        // Now using the parameterized header text
        JLabel welcomeLabel = new JLabel(headerText);
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        header.add(welcomeLabel);
        return header; 
    }

    private JPanel createAuthenticationPanel(){
        // Rest of the code remains the same
        JPanel authentication = new JPanel(); 
        authentication.setOpaque(false);
        authentication.setLayout(new BoxLayout(authentication, BoxLayout.Y_AXIS));
        authentication.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        authentication.add(Box.createVerticalGlue());

        JPanel buttonPanel = new JPanel(); 
        buttonPanel.setOpaque(false);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));
        JButton loginButton = new JButton("Login");
        JButton createAccountButton = new JButton("Create Account");

        loginButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        createAccountButton.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        loginButton.setPreferredSize(new Dimension(150, 40));
        createAccountButton.setPreferredSize(new Dimension(150, 40));

        buttonPanel.add(loginButton);
        buttonPanel.add(createAccountButton);
        authentication.add(buttonPanel);
        authentication.add(Box.createVerticalStrut(20));

        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parent.showPage("login");
            }
        });

        createAccountButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                parent.showPage("createAccount");
            }
        });

        return authentication; 
    }
}