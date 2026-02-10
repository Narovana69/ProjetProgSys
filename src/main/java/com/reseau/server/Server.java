package com.reseau.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.reseau.video.AudioStreamServer;
import com.reseau.video.VideoStreamServer;

/**
 * NEXO Server - Handles client connections and message routing
 * Uses TCP sockets on port 8080 with thread pool for concurrent clients
 */
public class Server {
    private static final int PORT = 8080;
    private static final int MAX_CLIENTS = 20;
    
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Map<String, ClientHandler> clients;
    private PresenceService presenceService;
    private AuthenticationService authService;
    private MessageStorage messageStorage;
    private volatile boolean running;

    public Server() {
        // Thread-safe map for concurrent client access
        this.clients = new ConcurrentHashMap<>();
        // Fixed thread pool to prevent thread explosion
        this.threadPool = Executors.newFixedThreadPool(MAX_CLIENTS);
        this.presenceService = new PresenceService(this);
        this.authService = new AuthenticationService();
        this.messageStorage = new MessageStorage();
        this.running = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
            System.out.println("=================================");
            System.out.println("NEXO Server started on port " + PORT);
            System.out.println("=================================");
            
            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New connection from: " + clientSocket.getInetAddress());
                    
                    // Create handler and submit to thread pool
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    threadPool.execute(handler);
                    
                } catch (SocketException e) {
                    if (!running) {
                        break; // Server shutdown
                    }
                    System.err.println("Socket error: " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Register a client handler
     */
    public void registerClient(String username, ClientHandler handler) {
        ClientHandler existing = clients.get(username);
        if (existing != null) {
            System.err.println("WARNING: Username '" + username + "' already registered! Replacing old connection.");
            existing.close();
        }
        clients.put(username, handler);
        System.out.println("Client registered: " + username + " (Total: " + clients.size() + ")");
    }

    /**
     * Unregister a client handler
     */
    public void unregisterClient(String username) {
        clients.remove(username);
        System.out.println("Client unregistered: " + username + " (Total: " + clients.size() + ")");
        
        // Broadcast updated user list to all remaining clients
        String userList = presenceService.getUserListString();
        broadcastRaw(userList);
        
        // Notify all clients about user leaving
        broadcast("SERVER", "all", username + " left the server");
    }

    /**
     * Broadcast message to all connected clients
     */
    public void broadcast(String sender, String recipient, String message) {
        String formattedMessage = "MESSAGE " + sender + " " + recipient + " " + message;
        
        for (ClientHandler client : clients.values()) {
            client.sendMessage(formattedMessage);
        }
    }

    /**
     * Broadcast raw message to all clients (for protocols like USER_LIST)
     */
    public void broadcastRaw(String message) {
        System.out.println("DEBUG: Server.broadcastRaw called with " + clients.size() + " clients");
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            System.out.println("DEBUG: Sending to client: " + entry.getKey());
            entry.getValue().sendMessage(message);
        }
    }

    public PresenceService getPresenceService() {
        return presenceService;
    }

    public AuthenticationService getAuthService() {
        return authService;
    }

    public MessageStorage getMessageStorage() {
        return messageStorage;
    }

    /**
     * Send message to specific client
     */
    public void sendToClient(String username, String message) {
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        running = false;
        
        // Shutdown presence service
        if (presenceService != null) {
            presenceService.shutdown();
        }
        
        // Shutdown message storage
        if (messageStorage != null) {
            messageStorage.shutdown();
        }
        
        // Close all client connections
        for (ClientHandler handler : clients.values()) {
            handler.close();
        }
        clients.clear();
        
        // Shutdown thread pool
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        
        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        
        System.out.println("Server shutdown complete");
    }

    public static void main(String[] args) {
        Server server = new Server();
        
        // Start video streaming server
        VideoStreamServer videoServer = new VideoStreamServer(VideoStreamServer.DEFAULT_PORT);
        videoServer.startAsync();
        
        // Start audio streaming server
        AudioStreamServer audioServer = new AudioStreamServer(AudioStreamServer.DEFAULT_PORT);
        audioServer.startAsync();
        
        // Shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            videoServer.stop();
            audioServer.stop();
            server.shutdown();
        }));
        
        server.start();
    }
}
