package com.reseau.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Client - Network layer for NEXO client
 * Handles TCP connection and message protocol
 */
public class Client {
    private static final int HEARTBEAT_INTERVAL = 5000; // 5 seconds
    
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private String username;
    private volatile boolean connected;
    private Thread listenerThread;
    private Thread heartbeatThread;
    private MessageListener messageListener;
    private List<String> messageBuffer = new CopyOnWriteArrayList<>();

    public interface MessageListener {
        void onMessageReceived(String message);
        void onConnectionLost();
    }

    public Client() {
        this.connected = false;
    }

    /**
     * Connect with authentication (username + password)
     */
    public boolean connectWithAuth(String host, int port, String username, String password) {
        try {
            System.out.println("Connecting to " + host + ":" + port);
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0);
            
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            // Send authentication request
            writer.println("AUTH " + username + " " + password);
            writer.flush();
            
            socket.setSoTimeout(5000);
            String response = reader.readLine();
            socket.setSoTimeout(0);
            
            System.out.println("Auth response: " + response);
            
            if (response != null && response.startsWith("AUTH_SUCCESS")) {
                this.username = username;
                this.connected = true;
                
                startMessageListener();
                startHeartbeat();
                
                System.out.println("Authenticated successfully as " + username);
                return true;
            } else {
                System.err.println("Authentication failed: " + response);
                disconnect();
            }
            
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            disconnect();
        }
        
        return false;
    }

    /**
     * Register new user account
     */
    public boolean register(String host, int port, String username, String password, 
                           String firstName, String lastName) {
        try {
            System.out.println("Registering with " + host + ":" + port);
            
            Socket tempSocket = new Socket();
            tempSocket.connect(new InetSocketAddress(host, port), 5000);
            
            BufferedReader tempReader = new BufferedReader(
                new InputStreamReader(tempSocket.getInputStream()));
            PrintWriter tempWriter = new PrintWriter(tempSocket.getOutputStream(), true);
            
            // Send registration request
            tempWriter.println("REGISTER " + username + " " + password + " " + 
                             firstName + " " + lastName);
            tempWriter.flush();
            
            tempSocket.setSoTimeout(5000);
            String response = tempReader.readLine();
            
            System.out.println("Registration response: " + response);
            
            boolean success = response != null && response.startsWith("REGISTER_SUCCESS");
            
            tempReader.close();
            tempWriter.close();
            tempSocket.close();
            
            return success;
            
        } catch (IOException e) {
            System.err.println("Registration failed: " + e.getMessage());
        }
        
        return false;
    }


    /**
     * Connect to server and authenticate
     */
    public boolean connect(String host, int port, String username) {
        try {
            System.out.println("Connecting to " + host + ":" + port);
            
            // Create socket and connect
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000); // 5s timeout
            
            // Configure socket
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(0); // No read timeout for persistent connection
            
            System.out.println("Socket connected, setting up I/O streams...");
            
            // Setup I/O streams
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            System.out.println("Sending CONNECT command: CONNECT " + username);
            
            // Send connection request
            writer.println("CONNECT " + username);
            writer.flush(); // Ensure data is sent
            
            System.out.println("Waiting for server response...");
            
            // Wait for confirmation with timeout
            socket.setSoTimeout(5000); // 5s timeout for initial response
            String response = reader.readLine();
            socket.setSoTimeout(0); // Reset to no timeout
            
            System.out.println("Server response: " + response);
            
            if (response != null && response.startsWith("CONNECTED")) {
                this.username = username;
                this.connected = true;
                
                // Start listener thread
                startMessageListener();
                
                // Start heartbeat thread
                startHeartbeat();
                
                System.out.println("Connected successfully as " + username);
                return true;
            } else {
                System.err.println("Invalid response from server: " + response);
                disconnect();
            }
            
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
            disconnect();
        }
        
        return false;
    }

    /**
     * Start background thread to listen for incoming messages
     */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = reader.readLine()) != null) {
                    System.out.println("Received: " + line);
                    
                    // Skip HISTORY messages during history playback
                    if (line.startsWith("HISTORY_START") || line.startsWith("HISTORY_END")) {
                        continue;
                    }
                    
                    if (messageListener != null) {
                        messageListener.onMessageReceived(line);
                    } else {
                        // Buffer messages until listener is set
                        System.out.println("DEBUG: Buffering message (listener not set yet): " + line.substring(0, Math.min(50, line.length())));
                        messageBuffer.add(line);
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Connection lost: " + e.getMessage());
                    if (messageListener != null) {
                        messageListener.onConnectionLost();
                    }
                }
            } finally {
                // Don't call disconnect here to avoid recursion
                // The disconnect will be handled by the close request
            }
        }, "MessageListener");
        
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Start heartbeat thread to send periodic presence updates
     */
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            String device = System.getProperty("os.name").replace("|", "-").replace(";", "-");
            String ip = getLocalIPAddress();
            
            while (connected) {
                try {
                    if (writer != null) {
                        writer.println("HEARTBEAT " + username + " " + device + " " + ip);
                    }
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    // Thread interrupted, exit gracefully
                    break;
                }
            }
        }, "HeartbeatThread");
        
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    /**
     * Get local IP address
     */
    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting IP address: " + e.getMessage());
        }
        return "127.0.0.1";
    }

    /**
     * Send message to server
     */
    public void sendMessage(String recipient, String message) {
        if (connected && writer != null) {
            String formatted = "MESSAGE " + username + " " + recipient + " " + message;
            writer.println(formatted);
            System.out.println("Sent: " + formatted);
        }
    }

    /**
     * Request user list refresh from server
     */
    public void refreshUserList() {
        if (connected && writer != null) {
            writer.println("REFRESH_USERS");
            System.out.println("Requesting user list refresh...");
        }
    }
    
    /**
     * Send friend request to another user
     */
    public void sendFriendRequest(String targetUsername) {
        if (connected && writer != null) {
            writer.println("FRIEND_REQUEST " + username + " " + targetUsername);
            System.out.println("Sending friend request to: " + targetUsername);
        }
    }
    
    /**
     * Accept a friend request
     */
    public void acceptFriendRequest(String requestId) {
        if (connected && writer != null) {
            writer.println("ACCEPT_FRIEND " + requestId + " " + username);
            System.out.println("Accepting friend request: " + requestId);
        }
    }
    
    /**
     * Reject a friend request
     */
    public void rejectFriendRequest(String requestId) {
        if (connected && writer != null) {
            writer.println("REJECT_FRIEND " + requestId + " " + username);
            System.out.println("Rejecting friend request: " + requestId);
        }
    }
    
    /**
     * Request list of friends
     */
    public void requestFriendsList() {
        if (connected && writer != null) {
            writer.println("GET_FRIENDS " + username);
            System.out.println("Requesting friends list...");
        }
    }
    
    /**
     * Request pending friend requests
     */
    public void requestPendingRequests() {
        if (connected && writer != null) {
            writer.println("GET_PENDING_REQUESTS " + username);
            System.out.println("Requesting pending friend requests...");
        }
    }
    
    /**
     * Check friendship status with another user
     */
    public void checkFriendship(String otherUsername) {
        if (connected && writer != null) {
            writer.println("CHECK_FRIENDSHIP " + username + " " + otherUsername);
            System.out.println("Checking friendship with: " + otherUsername);
        }
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        if (!connected && socket == null) {
            return; // Already disconnected
        }
        
        connected = false;
        
        // Stop heartbeat thread
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
        }
        
        // Notify server and close resources
        try {
            if (writer != null && socket != null && !socket.isClosed()) {
                writer.println("DISCONNECT");
                writer.flush();
            }
        } catch (Exception e) {
            System.err.println("Error sending disconnect: " + e.getMessage());
        }
        
        // Close resources gracefully
        try {
            if (reader != null) reader.close();
        } catch (Exception e) {}
        
        try {
            if (writer != null) writer.close();
        } catch (Exception e) {}
        
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception e) {}
        
        System.out.println("Disconnected");
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
        
        // Deliver any buffered messages
        if (!messageBuffer.isEmpty()) {
            System.out.println("DEBUG: Delivering " + messageBuffer.size() + " buffered messages");
            for (String bufferedMessage : messageBuffer) {
                if (messageListener != null) {
                    messageListener.onMessageReceived(bufferedMessage);
                }
            }
            messageBuffer.clear();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getUsername() {
        return username;
    }
}
