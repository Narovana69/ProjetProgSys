package com.reseau.server;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * RAID-1 disk manager for fault-tolerant message storage.
 * Writes to two separate files (primary and mirror) for redundancy.
 */
public class DiskManager {
    private final String primaryPath;
    private final String mirrorPath;
    
    public DiskManager(String primaryPath, String mirrorPath) {
        this.primaryPath = primaryPath;
        this.mirrorPath = mirrorPath;
    }
    
    /**
     * Save messages to both primary and mirror disks (RAID-1)
     */
    public void saveMessages(List<MessageStorage.StoredMessage> messages) throws IOException {
        boolean primarySuccess = false;
        boolean mirrorSuccess = false;
        IOException primaryException = null;
        IOException mirrorException = null;
        
        // Write to primary disk
        try {
            writeToFile(primaryPath, messages);
            primarySuccess = true;
        } catch (IOException e) {
            primaryException = e;
            System.err.println("Primary disk write failed: " + e.getMessage());
        }
        
        // Write to mirror disk
        try {
            writeToFile(mirrorPath, messages);
            mirrorSuccess = true;
        } catch (IOException e) {
            mirrorException = e;
            System.err.println("Mirror disk write failed: " + e.getMessage());
        }
        
        // If both fail, throw exception
        if (!primarySuccess && !mirrorSuccess) {
            throw new IOException("RAID-1 write failed on both disks", 
                primaryException != null ? primaryException : mirrorException);
        }
        
        // If only one succeeded, log warning
        if (!primarySuccess) {
            System.err.println("WARNING: Primary disk failed, running on mirror only");
        }
        if (!mirrorSuccess) {
            System.err.println("WARNING: Mirror disk failed, running on primary only");
        }
    }
    
    /**
     * Load messages from disk (tries primary first, falls back to mirror)
     */
    public List<MessageStorage.StoredMessage> loadMessages() throws IOException {
        // Try primary disk first
        try {
            List<MessageStorage.StoredMessage> messages = readFromFile(primaryPath);
            System.out.println("Loaded messages from primary disk");
            
            // Verify mirror matches primary
            verifyMirror(messages);
            
            return messages;
        } catch (IOException primaryError) {
            System.err.println("Primary disk read failed: " + primaryError.getMessage());
            
            // Fall back to mirror disk
            try {
                List<MessageStorage.StoredMessage> messages = readFromFile(mirrorPath);
                System.out.println("Loaded messages from mirror disk (primary failed)");
                
                // Restore primary from mirror
                try {
                    writeToFile(primaryPath, messages);
                    System.out.println("Restored primary disk from mirror");
                } catch (IOException e) {
                    System.err.println("Failed to restore primary: " + e.getMessage());
                }
                
                return messages;
            } catch (IOException mirrorError) {
                throw new IOException("Both disks failed to read", mirrorError);
            }
        }
    }
    
    /**
     * Write messages to a single file
     */
    private void writeToFile(String path, List<MessageStorage.StoredMessage> messages) throws IOException {
        File file = new File(path);
        File tempFile = new File(path + ".tmp");
        
        // Write to temp file first
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(tempFile)))) {
            oos.writeInt(messages.size());
            for (MessageStorage.StoredMessage msg : messages) {
                oos.writeObject(msg);
            }
        }
        
        // Atomic rename (replace old file with new)
        Files.move(tempFile.toPath(), file.toPath(), 
            StandardCopyOption.REPLACE_EXISTING, 
            StandardCopyOption.ATOMIC_MOVE);
    }
    
    /**
     * Read messages from a single file
     */
    @SuppressWarnings("unchecked")
    private List<MessageStorage.StoredMessage> readFromFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        List<MessageStorage.StoredMessage> messages = new ArrayList<>();
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            int count = ois.readInt();
            for (int i = 0; i < count; i++) {
                MessageStorage.StoredMessage msg = (MessageStorage.StoredMessage) ois.readObject();
                messages.add(msg);
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize messages", e);
        }
        
        return messages;
    }
    
    /**
     * Verify mirror disk matches primary (integrity check)
     */
    private void verifyMirror(List<MessageStorage.StoredMessage> primaryMessages) {
        try {
            File mirrorFile = new File(mirrorPath);
            if (!mirrorFile.exists()) {
                System.out.println("Mirror disk empty, will sync on next write");
                return;
            }
            
            List<MessageStorage.StoredMessage> mirrorMessages = readFromFile(mirrorPath);
            
            if (primaryMessages.size() != mirrorMessages.size()) {
                System.err.println("WARNING: Disk sync mismatch - Primary: " + 
                    primaryMessages.size() + ", Mirror: " + mirrorMessages.size());
            }
        } catch (IOException e) {
            System.err.println("Mirror verification failed: " + e.getMessage());
        }
    }
    
    /**
     * Check disk health status
     */
    public DiskStatus getStatus() {
        boolean primaryHealthy = checkDiskHealth(primaryPath);
        boolean mirrorHealthy = checkDiskHealth(mirrorPath);
        
        if (primaryHealthy && mirrorHealthy) {
            return DiskStatus.HEALTHY;
        } else if (primaryHealthy || mirrorHealthy) {
            return DiskStatus.DEGRADED;
        } else {
            return DiskStatus.FAILED;
        }
    }
    
    /**
     * Check if a disk is readable and writable
     */
    private boolean checkDiskHealth(String path) {
        File file = new File(path);
        return file.canRead() && file.canWrite();
    }
    
    public enum DiskStatus {
        HEALTHY,   // Both disks working
        DEGRADED,  // One disk failed
        FAILED     // Both disks failed
    }
}
