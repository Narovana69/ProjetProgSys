package com.reseau.client;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * NEXO Client Application - JavaFX Entry Point
 */
public class ClientApp extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        // Show new authenticated login window
        AuthLoginWindow loginWindow = new AuthLoginWindow(primaryStage);
        loginWindow.show();
    }

    @Override
    public void stop() {
        // Cleanup on application exit
        System.out.println("Application closing...");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
