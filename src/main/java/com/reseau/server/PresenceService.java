package com.reseau.server;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.reseau.common.UserInfo;
import com.reseau.common.UserInfo.UserStatus;

/**
 * PresenceService - Manages user presence and heartbeat monitoring
 * Tracks online status and detects inactive/offline users
 */
public class PresenceService {
    private static final long HEARTBEAT_TIMEOUT = 15000; // 15 seconds
    private static final long CHECK_INTERVAL = 5000; // Check every 5 seconds

    private Map<String, UserInfo> users;
    private ScheduledExecutorService scheduler;
    private Server server;

    public PresenceService(Server server) {
        this.server = server;
        this.users = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);
        
        // Start periodic check for inactive users
        startPresenceMonitoring();
    }

    private void startPresenceMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            checkInactiveUsers();
        }, CHECK_INTERVAL, CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * Register a new user
     */
    public void registerUser(String username, String device, String ipAddress) {
        UserInfo existing = users.get(username);
        if (existing != null) {
            System.out.println("DEBUG: User '" + username + "' already in presence map, updating...");
        }
        UserInfo userInfo = new UserInfo(username, device, ipAddress);
        users.put(username, userInfo);
        System.out.println("User registered: " + username + " from " + ipAddress + " (" + device + ")");
        System.out.println("DEBUG: Total users in map: " + users.size());
        
        // Broadcast user list update to all clients
        broadcastUserList();
        
        // Notify all clients about new user
        server.broadcast("SERVER", "all", username + " joined the server");
    }

    /**
     * Update user heartbeat
     */
    public void updateHeartbeat(String username, String device, String ipAddress) {
        UserInfo userInfo = users.get(username);
        if (userInfo != null) {
            userInfo.updateLastSeen();
            userInfo.setDevice(device);
            userInfo.setIpAddress(ipAddress);
        } else {
            // User not registered yet, register them
            registerUser(username, device, ipAddress);
        }
    }

    /**
     * Check for inactive/offline users
     */
    private void checkInactiveUsers() {
        boolean changed = false;
        
        for (UserInfo userInfo : users.values()) {
            if (userInfo.isTimedOut(HEARTBEAT_TIMEOUT)) {
                if (userInfo.getStatus() == UserStatus.ONLINE) {
                    userInfo.setStatus(UserStatus.INACTIVE);
                    System.out.println("User inactive: " + userInfo.getUsername());
                    changed = true;
                }
            }
        }
        
        if (changed) {
            broadcastUserList();
        }
    }

    /**
     * Mark user as disconnected
     */
    public void userDisconnected(String username) {
        UserInfo userInfo = users.get(username);
        if (userInfo != null) {
            userInfo.setStatus(UserStatus.OFFLINE);
            System.out.println("User disconnected: " + username);
            broadcastUserList();
            
            // Remove from map after a delay
            scheduler.schedule(() -> users.remove(username), 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Get all users
     */
    public Collection<UserInfo> getAllUsers() {
        return users.values();
    }

    /**
     * Broadcast user list to all clients
     */
    private void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USER_LIST");
        for (UserInfo userInfo : users.values()) {
            sb.append(";").append(userInfo.toString());
        }
        String message = sb.toString();
        System.out.println("DEBUG: PresenceService broadcasting: " + message);
        System.out.println("DEBUG: Users in presence map: " + users.size());
        server.broadcastRaw(message);
    }

    /**
     * Get user list as formatted string
     */
    public String getUserListString() {
        StringBuilder sb = new StringBuilder("USER_LIST");
        for (UserInfo userInfo : users.values()) {
            sb.append(";").append(userInfo.toString());
        }
        String result = sb.toString();
        System.out.println("DEBUG: getUserListString() returning: " + result);
        System.out.println("DEBUG: Current users.size() = " + users.size());
        return result;
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
