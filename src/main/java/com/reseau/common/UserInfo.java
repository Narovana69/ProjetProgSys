package com.reseau.common;

/**
 * UserInfo - Stores user presence information
 * Used for tracking online status, device type, and connection details
 */
public class UserInfo {
    private String username;
    private String device;
    private String ipAddress;
    private UserStatus status;
    private long lastSeen;

    public enum UserStatus {
        ONLINE,
        INACTIVE,
        OFFLINE
    }

    public UserInfo(String username, String device, String ipAddress) {
        this.username = username;
        this.device = device != null ? device.replace("|", "-").replace(";", "-") : "Unknown";
        this.ipAddress = ipAddress;
        this.status = UserStatus.ONLINE;
        this.lastSeen = System.currentTimeMillis();
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
        if (this.status == UserStatus.INACTIVE) {
            this.status = UserStatus.ONLINE;
        }
    }

    public boolean isTimedOut(long timeoutMs) {
        return System.currentTimeMillis() - lastSeen > timeoutMs;
    }

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device != null ? device.replace("|", "-").replace(";", "-") : "Unknown";
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    @Override
    public String toString() {
        return String.format("%s|%s|%s|%s", username, device, ipAddress, status);
    }

    public static UserInfo fromString(String str) {
        try {
            String[] parts = str.split("\\|");
            if (parts.length >= 4) {
                UserInfo info = new UserInfo(parts[0], parts[1], parts[2]);
                info.setStatus(UserStatus.valueOf(parts[3]));
                return info;
            }
            System.err.println("Invalid UserInfo format (not enough parts): " + str);
        } catch (Exception e) {
            System.err.println("Error parsing UserInfo: " + str + " - " + e.getMessage());
        }
        return null;
    }
}
