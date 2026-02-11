package com.reseau.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * LoginWindow - Initial connection screen
 */
public class LoginWindow {
    private Stage stage;
    private TextField usernameField;
    private TextField serverField;
    private Button connectButton;
    private Label statusLabel;

    public LoginWindow(Stage stage) {
        this.stage = stage;
        setupUI();
    }

    private void setupUI() {
        // Main container with gradient background
        StackPane mainContainer = new StackPane();
        mainContainer.setStyle(
            "-fx-background-color: linear-gradient(to bottom right, #667eea 0%, #764ba2 100%);"
        );

        // Login card with shadow effect
        VBox loginCard = new VBox(20);
        loginCard.setPadding(new Insets(40, 50, 40, 50));
        loginCard.setAlignment(Pos.CENTER);
        loginCard.setMaxWidth(450);
        loginCard.setStyle(
            "-fx-background-color: white; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 5);"
        );

        // Title with icon
        Label titleLabel = new Label("⚡ NEXO");
        titleLabel.setStyle(
            "-fx-font-size: 42px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 0, 2);"
        );

        Label subtitleLabel = new Label("Connect to start chatting");
        subtitleLabel.setStyle(
            "-fx-font-size: 15px; " +
            "-fx-text-fill: #6c757d; " +
            "-fx-font-weight: 500;"
        );

        // Spacer
        Region spacer1 = new Region();
        spacer1.setPrefHeight(10);

        // Username input with modern styling
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

        // Server address input
        Label serverLabel = new Label("SERVER ADDRESS");
        serverLabel.setStyle(
            "-fx-font-size: 11px; " +
            "-fx-font-weight: bold; " +
            "-fx-text-fill: #667eea; " +
            "-fx-letter-spacing: 1px;"
        );
        
        serverField = new TextField("localhost:4444");
        serverField.setPromptText("host:port");
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

        // Spacer
        Region spacer2 = new Region();
        spacer2.setPrefHeight(10);

        // Connect button with gradient
        connectButton = new Button("🚀 Connect Now");
        connectButton.setPrefWidth(350);
        connectButton.setPrefHeight(50);
        connectButton.setStyle(
            "-fx-font-size: 16px; " +
            "-fx-font-weight: bold; " +
            "-fx-background-color: linear-gradient(to right, #667eea, #764ba2); " +
            "-fx-text-fill: white; " +
            "-fx-background-radius: 10px; " +
            "-fx-cursor: hand; " +
            "-fx-effect: dropshadow(gaussian, rgba(102, 126, 234, 0.4), 10, 0, 0, 3);"
        );
        connectButton.setOnAction(e -> handleConnect());

        // Status label
        statusLabel = new Label("");
        statusLabel.setStyle(
            "-fx-text-fill: #e74c3c; " +
            "-fx-font-size: 13px; " +
            "-fx-font-weight: 500;"
        );
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(350);
        statusLabel.setAlignment(Pos.CENTER);

        // Add all elements to card
        loginCard.getChildren().addAll(
            titleLabel,
            subtitleLabel,
            spacer1,
            usernameLabel,
            usernameField,
            serverLabel,
            serverField,
            spacer2,
            connectButton,
            statusLabel
        );

        mainContainer.getChildren().add(loginCard);

        // Enter key to connect
        usernameField.setOnAction(e -> handleConnect());
        serverField.setOnAction(e -> handleConnect());

        Scene scene = new Scene(mainContainer, 550, 650);
        stage.setScene(scene);
        stage.setTitle("NEXO - Sign In");
        stage.setResizable(false);
    }

    private void handleConnect() {
        String username = usernameField.getText().trim();
        String serverAddr = serverField.getText().trim();

        // Validation
        if (username.isEmpty()) {
            showStatus("Please enter a username", true);
            return;
        }

        if (serverAddr.isEmpty()) {
            showStatus("Please enter server address", true);
            return;
        }

        // Parse server address
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

        // Prevent multiple connection attempts
        if (connectButton.isDisabled()) {
            return;
        }

        // Attempt connection
        connectButton.setDisable(true);
        showStatus("Connecting...", false);

        // Connect in background thread
        Thread connectThread = new Thread(() -> {
            Client client = new Client();
            boolean success = false;
            
            try {
                success = client.connect(host, port, username);
            } catch (Exception e) {
                System.err.println("Connection error: " + e.getMessage());
                e.printStackTrace();
            }
            
            final boolean finalSuccess = success;

            javafx.application.Platform.runLater(() -> {
                if (finalSuccess) {
                    System.out.println("Successfully connected, opening chat window...");
                    // Open chat window
                    ChatWindow chatWindow = new ChatWindow(stage, client);
                    chatWindow.show();
                } else {
                    System.out.println("Connection failed");
                    showStatus("Connection failed. Is the server running?", true);
                    connectButton.setDisable(false);
                }
            });
        });
        connectThread.setName("ConnectionThread");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: " + 
            (isError ? "#e74c3c" : "#27ae60") + ";"
        );
    }

    public void show() {
        stage.show();
    }
}
