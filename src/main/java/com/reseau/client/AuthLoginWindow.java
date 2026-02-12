package com.reseau.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.util.Properties;
public class AuthLoginWindow {
    private Stage stage;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField serverField;
    private Button loginButton;
    private Button registerButton;
    private Label statusLabel;
    private Hyperlink adminLink;
    private static final String CONFIG_FILE = ".nexo_config.properties";

    public AuthLoginWindow(Stage stage) {
        this.stage = stage;
        setupUI();
    }

    private void setupUI() {
        StackPane mainContainer = new StackPane();
        mainContainer.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);"
        );

        VBox loginCard = new VBox(20);
        loginCard.setPadding(new Insets(40, 50, 40, 50));
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setMaxWidth(450);
        loginCard.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );

        Label titleLabel = new Label("⚡ NEXO");
        titleLabel.setStyle(
            "-fx-font-size: 42px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);"
        );

        Label subtitleLabel = new Label("Sign in to your account");
        subtitleLabel.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-text-fill: #6c757d; " +
            "-fx-font-weight: 500;"
        );

        Region spacer1 = new Region();
        spacer1.setPrefHeight(10);

        // Username
        Label usernameLabel = new Label("USERNAME");
        usernameLabel.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea; " +
            "-fx-letter-spacing: 1px;"
        );
        
        usernameField = new TextField();
        usernameField.setPromptText("Enter your username");
        usernameField.setPrefWidth(350);
        usernameField.setPrefHeight(45);
        usernameField.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-padding: 12px 15px; " +
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: #e9ecef; " +
            "-fx-border-radius: 8px; " +
            "-fx-border-width: 2px;"
        );

        // Password
        Label passwordLabel = new Label("PASSWORD");
        passwordLabel.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea; " +
            "-fx-letter-spacing: 1px;"
        );
        
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter your password");
        passwordField.setPrefWidth(350);
        passwordField.setPrefHeight(45);
        passwordField.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-padding: 12px 15px; " +
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: #e9ecef; " +
            "-fx-border-radius: 8px; " +
            "-fx-border-width: 2px;"
        );

        // Server address
        Label serverLabel = new Label("SERVER ADDRESS");
        serverLabel.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea; " +
            "-fx-letter-spacing: 1px;"
        );
        
        serverField = new TextField();
        serverField.setPromptText("host:port");
        serverField.setText(loadLastServer());
        serverField.setPrefWidth(350);
        serverField.setPrefHeight(45);
        serverField.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-padding: 12px 15px; " +
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 8px; " +
            "-fx-border-color: #e9ecef; " +
            "-fx-border-radius: 8px; " +
            "-fx-border-width: 2px;"
        );

        Region spacer2 = new Region();
        spacer2.setPrefHeight(10);

        // Login button
        loginButton = new Button("Sign In 🚀");
        loginButton.setPrefWidth(350);
        loginButton.setPrefHeight(50);
        loginButton.setStyle(
            "-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 16px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 25px; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(102, 126, 234, 0.4), 12, 0, 0, 4);"
        );
        loginButton.setOnAction(e -> handleLogin());

        // Register button
        registerButton = new Button("Create New Account");
        registerButton.setPrefWidth(350);
        registerButton.setPrefHeight(45);
        registerButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: #667eea; " +
            "-fx-font-size: 14px; " +
            "-fx-cursor: hand; " +
            "-fx-border-color: #667eea; " +
            "-fx-border-radius: 22px; " +
            "-fx-background-radius: 22px; " +
            "-fx-border-width: 2;"
        );
        registerButton.setOnAction(e -> openRegistration());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 13px;");

        // Admin link (hidden - Ctrl+Shift+A to access)
        adminLink = new Hyperlink("🔒 Admin Access");
        adminLink.setVisible(false);
        adminLink.setStyle("-fx-font-size: 10px; -fx-text-fill: #95a5a6;");
        adminLink.setOnAction(e -> showAdminAccess());

        loginCard.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            spacer1,
            usernameLabel,
            usernameField,
            passwordLabel,
            passwordField,
            serverLabel,
            serverField,
            spacer2,
            loginButton,
            registerButton,
            statusLabel,
            adminLink
        );

        mainContainer.getChildren().add(loginCard);

        // Enter key to login
        usernameField.setOnAction(e -> handleLogin());
        passwordField.setOnAction(e -> handleLogin());
        serverField.setOnAction(e -> handleLogin());

        Scene scene = new Scene(mainContainer, 550, 700);
        
        // Secret key combination: Ctrl+Shift+A for admin
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.isShiftDown() && e.getCode().toString().equals("A")) {
                adminLink.setVisible(!adminLink.isVisible());
            }
        });

        stage.setScene(scene);
        stage.setTitle("NEXO - Sign In");
        stage.setResizable(false);
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String serverAddr = serverField.getText().trim();

        if (username.isEmpty()) {
            showStatus("Please enter your username", true);
            return;
        }

        if (password.isEmpty()) {
            showStatus("Please enter your password", true);
            return;
        }

        if (serverAddr.isEmpty()) {
            showStatus("Please enter server address", true);
            return;
        }

        String[] parts = serverAddr.split(":");
        if (parts.length != 2) {
            showStatus("Invalid format. Use host:port", true);
            return;
        }

        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            showStatus("Invalid port number", true);
            return;
        }

        loginButton.setDisable(true);
        showStatus("Signing in...", false);

        // Save server address
        saveLastServer(serverAddr);

        Thread loginThread = new Thread(() -> {
            Client client = new Client();
            boolean success = client.connectWithAuth(host, port, username, password);

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    ChatWindow chatWindow = new ChatWindow(stage, client);
                    chatWindow.show();
                } else {
                    showStatus("Login failed. Check username and password.", true);
                    loginButton.setDisable(false);
                }
            });
        });
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void openRegistration() {
        RegistrationWindow registrationWindow = new RegistrationWindow(stage);
        registrationWindow.show();
    }

    private void showAdminAccess() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Admin Access");
        dialog.setHeaderText("🔐 Enter Master Password");
        dialog.setContentText("Password:");
        
        dialog.showAndWait().ifPresent(masterPassword -> {
            // This would verify with server
            showStatus("Admin verification in progress...", false);
        });
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(
            "-fx-font-size: 13px; -fx-text-fill: " + 
            (isError ? "#e74c3c" : "#27ae60") + ";"
        );
    }

    private void saveLastServer(String server) {
        Properties props = new Properties();
        props.setProperty("last.server", server);
        try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
            props.store(os, "NEXO Client Configuration");
        } catch (IOException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    private String loadLastServer() {
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(CONFIG_FILE)) {
            props.load(is);
            return props.getProperty("last.server", "localhost:8080");
        } catch (IOException e) {
            return "localhost:8080";
        }
    }

    public void show() {
        stage.show();
    }
}
