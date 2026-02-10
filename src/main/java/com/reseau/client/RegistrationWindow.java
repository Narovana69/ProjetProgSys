package com.reseau.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.util.Properties;

/**
 * RegistrationWindow - User signup interface
 */
public class RegistrationWindow {
    private Stage stage;
    private TextField firstNameField;
    private TextField lastNameField;
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField serverField;
    private Button registerButton;
    private Button backButton;
    private Label statusLabel;
    private static final String CONFIG_FILE = ".nexo_config.properties";

    public RegistrationWindow(Stage stage) {
        this.stage = stage;
        setupUI();
    }

    private void setupUI() {
        StackPane mainContainer = new StackPane();
        mainContainer.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);"
        );

        VBox registrationCard = new VBox(15);
        registrationCard.setPadding(new Insets(35, 45, 35, 45));
        registrationCard.setAlignment(Pos.CENTER);
        registrationCard.setMaxWidth(500);
        registrationCard.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );

        Label titleLabel = new Label("✨ Create Account");
        titleLabel.setStyle(
            "-fx-font-size: 32px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );

        Label subtitleLabel = new Label("Join NEXO today");
        subtitleLabel.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-text-fill: #6c757d;"
        );

        Region spacer1 = new Region();
        spacer1.setPrefHeight(5);

        // First Name
        Label firstNameLabel = new Label("FIRST NAME");
        firstNameLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        firstNameField = createTextField("Enter your first name");

        // Last Name
        Label lastNameLabel = new Label("LAST NAME");
        lastNameLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        lastNameField = createTextField("Enter your last name");

        // Username
        Label usernameLabel = new Label("USERNAME");
        usernameLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        usernameField = createTextField("Choose a unique username");
        
        // Check username availability on input
        usernameField.textProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && !newVal.trim().isEmpty() && newVal.length() > 2) {
                checkUsernameAvailability(newVal.trim());
            }
        });

        // Password
        Label passwordLabel = new Label("PASSWORD");
        passwordLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        passwordField = createPasswordField("Minimum 6 characters");

        // Confirm Password
        Label confirmPasswordLabel = new Label("CONFIRM PASSWORD");
        confirmPasswordLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        confirmPasswordField = createPasswordField("Re-enter your password");

        // Server Address
        Label serverLabel = new Label("SERVER ADDRESS");
        serverLabel.setStyle(
            "-fx-font-size: 10px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea;"
        );
        serverField = createTextField("host:port");
        serverField.setText(loadLastServer());

        Region spacer2 = new Region();
        spacer2.setPrefHeight(5);

        // Buttons
        registerButton = new Button("Create Account 🚀");
        registerButton.setPrefWidth(400);
        registerButton.setPrefHeight(45);
        registerButton.setStyle(
            "-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 15px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 25px; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(102, 126, 234, 0.4), 10, 0, 0, 3);"
        );
        registerButton.setOnAction(e -> handleRegistration());

        backButton = new Button("← Back to Login");
        backButton.setPrefWidth(400);
        backButton.setPrefHeight(40);
        backButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: #667eea; " +
            "-fx-font-size: 13px; " +
            "-fx-cursor: hand; " +
            "-fx-border-color: #667eea; " +
            "-fx-border-radius: 20px; " +
            "-fx-background-radius: 20px; " +
            "-fx-border-width: 2;"
        );
        backButton.setOnAction(e -> goBackToLogin());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px;");

        registrationCard.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            spacer1,
            firstNameLabel, firstNameField,
            lastNameLabel, lastNameField,
            usernameLabel, usernameField,
            passwordLabel, passwordField,
            confirmPasswordLabel, confirmPasswordField,
            serverLabel, serverField,
            spacer2,
            registerButton,
            backButton,
            statusLabel
        );

        mainContainer.getChildren().add(registrationCard);

        ScrollPane scrollPane = new ScrollPane(mainContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        Scene scene = new Scene(scrollPane, 600, 750);
        stage.setScene(scene);
        stage.setTitle("NEXO - Registration");
    }

    private TextField createTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        field.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-padding: 10px 12px; " +
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 6px; " +
            "-fx-border-color: #e9ecef; " +
            "-fx-border-radius: 6px; " +
            "-fx-border-width: 2px;"
        );
        return field;
    }

    private PasswordField createPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setPrefHeight(40);
        field.setStyle(
            "-fx-font-size: 13px; " +
            "-fx-padding: 10px 12px; " +
            "-fx-background-color: #f8f9fa; " +
            "-fx-background-radius: 6px; " +
            "-fx-border-color: #e9ecef; " +
            "-fx-border-radius: 6px; " +
            "-fx-border-width: 2px;"
        );
        return field;
    }

    private void checkUsernameAvailability(String username) {
        // This will be implemented with server check
        // For now, just visual feedback
    }

    private void handleRegistration() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String serverAddr = serverField.getText().trim();

        // Validation
        if (firstName.isEmpty() || lastName.isEmpty()) {
            showStatus("Please enter your full name", true);
            return;
        }

        if (username.isEmpty() || username.length() < 3) {
            showStatus("Username must be at least 3 characters", true);
            return;
        }

        if (password.isEmpty() || password.length() < 6) {
            showStatus("Password must be at least 6 characters", true);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showStatus("Passwords do not match", true);
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

        registerButton.setDisable(true);
        showStatus("Registering...", false);

        // Save server address
        saveLastServer(serverAddr);

        // Connect and register
        Thread registerThread = new Thread(() -> {
            Client client = new Client();
            boolean success = client.register(host, port, username, password, firstName, lastName);

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    showStatus("Registration successful! Please login.", false);
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {}
                    goBackToLogin();
                } else {
                    showStatus("Registration failed. Username may already exist.", true);
                    registerButton.setDisable(false);
                }
            });
        });
        registerThread.setDaemon(true);
        registerThread.start();
    }

    private void goBackToLogin() {
        AuthLoginWindow loginWindow = new AuthLoginWindow(stage);
        loginWindow.show();
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + 
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
