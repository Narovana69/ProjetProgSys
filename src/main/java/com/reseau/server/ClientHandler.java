package com.reseau.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientHandler - Manages individual client connection
 * Runs in separate thread from thread pool
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private Server server;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private volatile boolean running;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
        
        try {
            // Configure socket for optimal TCP performance
            socket.setKeepAlive(true);  // Enable TCP keep-alive
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for low latency
            
            // Setup I/O streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            
        } catch (IOException e) {
            System.err.println("Error setting up client handler: " + e.getMessage());
            close();
        }
    }

    @Override
    public void run() {
        try {
            // First message should be AUTH, REGISTER, or legacy CONNECT
            String firstMessage = reader.readLine();
            
            if (firstMessage == null) {
                return;
            }
            
            if (firstMessage.startsWith("AUTH ")) {
                handleAuth(firstMessage);
            } else if (firstMessage.startsWith("REGISTER ")) {
                handleRegister(firstMessage);
            } else if (firstMessage.startsWith("CONNECT ")) {
                handleLegacyConnect(firstMessage);
            } else {
                sendMessage("ERROR Unknown command");
                close();
            }
            
        } catch (IOException e) {
            if (running) {
                System.err.println("Client connection error: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void handleAuth(String message) throws IOException {
        // Format: AUTH username password
        String[] parts = message.split(" ", 3);
        if (parts.length < 3) {
            sendMessage("AUTH_FAILED Invalid format");
            return;
        }
        
        String username = parts[1];
        String password = parts[2];
        
        com.reseau.common.UserAccount account = server.getAuthService().authenticate(username, password);
        if (account != null) {
            this.username = username;
            sendMessage("AUTH_SUCCESS " + account.getFullName());
            
            System.out.println("DEBUG: ClientHandler - User authenticated: " + username);
            
            // Register client and start session
            server.registerClient(username, this);
            
            String clientIp = socket.getInetAddress().getHostAddress();
            server.getPresenceService().registerUser(username, "Unknown", clientIp);
            
            // Send message history
            sendMessageHistory();
            
            System.out.println("DEBUG: ClientHandler - " + username + " fully registered, entering message loop");
            
            // Main message loop
            String line;
            while (running && (line = reader.readLine()) != null) {
                handleMessage(line);
            }
        } else {
            sendMessage("AUTH_FAILED Invalid credentials");
        }
    }

    private void handleRegister(String message) {
        // Format: REGISTER username password firstName lastName
        String[] parts = message.split(" ", 5);
        if (parts.length < 5) {
            sendMessage("REGISTER_FAILED Invalid format");
            return;
        }
        
        String username = parts[1];
        String password = parts[2];
        String firstName = parts[3];
        String lastName = parts[4];
        
        boolean success = server.getAuthService().registerUser(username, password, firstName, lastName);
        if (success) {
            sendMessage("REGISTER_SUCCESS");
        } else {
            sendMessage("REGISTER_FAILED Username already exists");
        }
    }

    private void handleLegacyConnect(String message) throws IOException {
        // Legacy support for old CONNECT command (no auth)
        username = message.substring(8).trim();
        
        System.out.println("DEBUG: ClientHandler - Received legacy CONNECT for username: " + username);
        
        sendMessage("CONNECTED " + username);
        
        System.out.println("DEBUG: ClientHandler - Registering " + username + " in server.clients");
        server.registerClient(username, this);
        
        System.out.println("DEBUG: ClientHandler - Registering " + username + " in presence service");
        String clientIp = socket.getInetAddress().getHostAddress();
        server.getPresenceService().registerUser(username, "Unknown", clientIp);
        
        System.out.println("DEBUG: ClientHandler - " + username + " fully registered, entering message loop");
        
        // Main message loop
        String line;
        while (running && (line = reader.readLine()) != null) {
            handleMessage(line);
        }
    }

    /**
     * Process incoming messages based on protocol
     */
    private void handleMessage(String message) {
        System.out.println("Received: " + message);
        
        String[] parts = message.split(" ", 4);
        if (parts.length < 2) {
            return;
        }
        
        String command = parts[0];
        
        switch (command) {
            case "MESSAGE":
                // Format: MESSAGE <sender> <recipient> <text>
                if (parts.length >= 4) {
                    String sender = parts[1];
                    String recipient = parts[2];
                    String text = parts[3];
                    
                    // Store message to disk (RAID-1)
                    server.getMessageStorage().storeMessage(sender, recipient, text);
                    
                    if (recipient.equals("all")) {
                        // Broadcast to all clients
                        server.broadcast(sender, recipient, text);
                    } else {
                        // Private message - send to recipient
                        System.out.println("DEBUG: Private message from " + sender + " to " + recipient);
                        server.sendToClient(recipient, message);
                        // Also send back to sender for confirmation (they see their own message)
                        if (!sender.equals(recipient)) {
                            server.sendToClient(sender, message);
                        }
                    }
                }
                break;
                
            case "HEARTBEAT":
                // Format: HEARTBEAT <username> <device> <ip>
                if (parts.length >= 4) {
                    String hbUsername = parts[1];
                    String device = parts[2];
                    String ip = parts[3];
                    server.getPresenceService().updateHeartbeat(hbUsername, device, ip);
                }
                break;
                
            case "REFRESH_USERS":
                // Client requesting current user list
                System.out.println("DEBUG: Client " + username + " requested user list refresh");
                sendUserList();
                break;
                
            case "DISCONNECT":
                running = false;
                break;
                
            default:
                System.out.println("Unknown command: " + command);
        }
    }

    /**
     * Send message to this client
     */
    public void sendMessage(String message) {
        if (writer != null) {
            System.out.println("DEBUG: ClientHandler sending to " + username + ": " + message.substring(0, Math.min(50, message.length())) + "...");
            writer.println(message);
            writer.flush();
        } else {
            System.err.println("DEBUG: Cannot send to " + username + " - writer is null");
        }
    }

    /**
     * Send current user list to this client
     */
    private void sendUserList() {
        String userList = server.getPresenceService().getUserListString();
        System.out.println("DEBUG: Sending USER_LIST: " + userList);
        sendMessage(userList);
    }

    /**
     * Send message history to client on login
     */
    private void sendMessageHistory() {
        try {
            java.util.List<MessageStorage.StoredMessage> history = 
                server.getMessageStorage().getRecentMessages(username, 100);
            
            if (!history.isEmpty()) {
                sendMessage("HISTORY_START");
                for (MessageStorage.StoredMessage msg : history) {
                    // Format: HISTORY <timestamp> <sender> <recipient> <text>
                    String historyLine = String.format("HISTORY %d %s %s %s",
                        msg.timestamp, msg.sender, msg.recipient, msg.content);
                    sendMessage(historyLine);
                }
                sendMessage("HISTORY_END");
                System.out.println("Sent " + history.size() + " messages from history to " + username);
            }
        } catch (Exception e) {
            System.err.println("Failed to send message history: " + e.getMessage());
        }
    }

    /**
     * Close connection and cleanup resources
     */
    public void close() {
        running = false;
        
        if (username != null) {
            server.getPresenceService().userDisconnected(username);
            server.unregisterClient(username);
        }
        
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing client handler: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}
