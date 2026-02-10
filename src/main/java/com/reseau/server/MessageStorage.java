package com.reseau.server;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Message storage with RAID-1 mirroring for fault tolerance.
 * Stores all chat messages to disk with automatic backup.
 */
public class MessageStorage {
    private static final String PRIMARY_FILE = ".nexo_messages_primary.dat";
    private static final String MIRROR_FILE = ".nexo_messages_mirror.dat";
    private static final int MAX_MESSAGES = 10000;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DiskManager diskManager;
    private final ConcurrentLinkedQueue<StoredMessage> messageQueue;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean running = true;
    private Thread writerThread;
    
    public MessageStorage() {
        this.diskManager = new DiskManager(PRIMARY_FILE, MIRROR_FILE);
        this.messageQueue = new ConcurrentLinkedQueue<>();
        loadExistingMessages();
        startWriterThread();
    }
    
    /**
     * Store a new message (async write to disk)
     */
    public void storeMessage(String sender, String recipient, String content) {
        StoredMessage msg = new StoredMessage(
            System.currentTimeMillis(),
            sender,
            recipient,
            content
        );
        
        lock.writeLock().lock();
        try {
            messageQueue.offer(msg);
            
            // Trim old messages if exceeds limit
            while (messageQueue.size() > MAX_MESSAGES) {
                messageQueue.poll();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Retrieve recent messages for a user (last 100)
     */
    public List<StoredMessage> getRecentMessages(String username, int limit) {
        List<StoredMessage> userMessages = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            for (StoredMessage msg : messageQueue) {
                if (msg.sender.equals(username) || 
                    msg.recipient.equals(username) || 
                    msg.recipient.equalsIgnoreCase("ALL")) {
                    userMessages.add(msg);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Return last N messages
        int fromIndex = Math.max(0, userMessages.size() - limit);
        return userMessages.subList(fromIndex, userMessages.size());
    }
    
    /**
     * Get all messages between two users
     */
    public List<StoredMessage> getConversation(String user1, String user2, int limit) {
        List<StoredMessage> conversation = new ArrayList<>();
        
        lock.readLock().lock();
        try {
            for (StoredMessage msg : messageQueue) {
                boolean isConversation = 
                    (msg.sender.equals(user1) && msg.recipient.equals(user2)) ||
                    (msg.sender.equals(user2) && msg.recipient.equals(user1)) ||
                    msg.recipient.equalsIgnoreCase("ALL");
                
                if (isConversation) {
                    conversation.add(msg);
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // Return last N messages
        int fromIndex = Math.max(0, conversation.size() - limit);
        return conversation.subList(fromIndex, conversation.size());
    }
    
    /**
     * Load existing messages from disk on startup
     */
    private void loadExistingMessages() {
        try {
            List<StoredMessage> loaded = diskManager.loadMessages();
            messageQueue.addAll(loaded);
            System.out.println("Loaded " + loaded.size() + " messages from storage");
        } catch (IOException e) {
            System.err.println("Failed to load messages: " + e.getMessage());
            System.err.println("Starting with empty message history");
        }
    }
    
    /**
     * Background thread that periodically flushes messages to disk
     */
    private void startWriterThread() {
        writerThread = new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000); // Flush every 5 seconds
                    flushToDisk();
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Message flush error: " + e.getMessage());
                }
            }
        }, "MessageWriter");
        writerThread.setDaemon(true);
        writerThread.start();
    }
    
    /**
     * Write all messages to disk with RAID-1 mirroring
     */
    private void flushToDisk() {
        lock.readLock().lock();
        try {
            List<StoredMessage> messages = new ArrayList<>(messageQueue);
            diskManager.saveMessages(messages);
        } catch (IOException e) {
            System.err.println("Failed to save messages: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Shutdown storage and flush remaining messages
     */
    public void shutdown() {
        running = false;
        if (writerThread != null) {
            writerThread.interrupt();
        }
        flushToDisk();
        System.out.println("Message storage shutdown complete");
    }
    
    /**
     * Stored message structure
     */
    public static class StoredMessage implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final long timestamp;
        public final String sender;
        public final String recipient;
        public final String content;
        
        public StoredMessage(long timestamp, String sender, String recipient, String content) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.recipient = recipient;
            this.content = content;
        }
        
        public String getFormattedTimestamp() {
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
            );
            return dateTime.format(TIMESTAMP_FORMAT);
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s → %s: %s",
                getFormattedTimestamp(), sender, recipient, content);
        }
    }
}
