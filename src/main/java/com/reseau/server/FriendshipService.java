package com.reseau.server;

import com.reseau.common.FriendRequest;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * FriendshipService - Manages friend requests and friendships
 */
public class FriendshipService {
    private static final String FRIENDS_DB_FILE = ".nexo_friends.dat";
    private static final String REQUESTS_DB_FILE = ".nexo_friend_requests.dat";
    
    // Username -> Set of friend usernames
    private Map<String, Set<String>> friendships;
    
    // RequestId -> FriendRequest
    private Map<String, FriendRequest> friendRequests;
    
    // Username -> List of pending request IDs (received)
    private Map<String, List<String>> pendingRequests;
    
    // Username -> List of sent request IDs
    private Map<String, List<String>> sentRequests;
    
    public FriendshipService() {
        this.friendships = new ConcurrentHashMap<>();
        this.friendRequests = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.sentRequests = new ConcurrentHashMap<>();
        loadData();
    }
    
    /**
     * Send a friend request
     */
    public synchronized FriendRequest sendFriendRequest(String senderUsername, String receiverUsername) {
        // ✅ Prevent self-friending
        if (senderUsername.equals(receiverUsername)) {
            System.out.println("Blocked self-friend request: " + senderUsername);
            return null; // Cannot add yourself
        }
        
        // Check if already friends
        if (areFriends(senderUsername, receiverUsername)) {
            return null; // Already friends
        }
        
        // Check if request already exists
        if (hasPendingRequest(senderUsername, receiverUsername)) {
            return null; // Request already sent
        }
        
        // Create new request
        FriendRequest request = new FriendRequest(senderUsername, receiverUsername);
        friendRequests.put(request.getRequestId(), request);
        
        // Add to pending requests for receiver
        pendingRequests.computeIfAbsent(receiverUsername, k -> new ArrayList<>())
                       .add(request.getRequestId());
        
        // Add to sent requests for sender
        sentRequests.computeIfAbsent(senderUsername, k -> new ArrayList<>())
                    .add(request.getRequestId());
        
        saveData();
        System.out.println("Friend request sent: " + senderUsername + " -> " + receiverUsername);
        return request;
    }
    
    /**
     * Accept a friend request
     */
    public synchronized boolean acceptFriendRequest(String requestId, String username) {
        FriendRequest request = friendRequests.get(requestId);
        if (request == null || !request.isPending()) {
            return false;
        }
        
        // Verify that username is the receiver
        if (!request.getReceiverUsername().equals(username)) {
            return false;
        }
        
        // Accept the request
        request.accept();
        
        // Add friendship both ways
        String sender = request.getSenderUsername();
        String receiver = request.getReceiverUsername();
        
        friendships.computeIfAbsent(sender, k -> ConcurrentHashMap.newKeySet()).add(receiver);
        friendships.computeIfAbsent(receiver, k -> ConcurrentHashMap.newKeySet()).add(sender);
        
        // Remove from pending
        List<String> pending = pendingRequests.get(receiver);
        if (pending != null) {
            pending.remove(requestId);
        }
        
        saveData();
        System.out.println("Friend request accepted: " + sender + " <-> " + receiver);
        return true;
    }
    
    /**
     * Reject a friend request
     */
    public synchronized boolean rejectFriendRequest(String requestId, String username) {
        FriendRequest request = friendRequests.get(requestId);
        if (request == null || !request.isPending()) {
            return false;
        }
        
        // Verify that username is the receiver
        if (!request.getReceiverUsername().equals(username)) {
            return false;
        }
        
        // Reject the request
        request.reject();
        
        // Remove from pending
        List<String> pending = pendingRequests.get(username);
        if (pending != null) {
            pending.remove(requestId);
        }
        
        saveData();
        System.out.println("Friend request rejected: " + requestId);
        return true;
    }
    
    /**
     * Check if two users are friends
     */
    public boolean areFriends(String username1, String username2) {
        Set<String> friends = friendships.get(username1);
        return friends != null && friends.contains(username2);
    }
    
    /**
     * Get list of friends for a user
     */
    public Set<String> getFriends(String username) {
        return friendships.getOrDefault(username, Collections.emptySet());
    }
    
    /**
     * Get pending friend requests received by user
     */
    public List<FriendRequest> getPendingRequests(String username) {
        List<String> requestIds = pendingRequests.getOrDefault(username, Collections.emptyList());
        return requestIds.stream()
                         .map(friendRequests::get)
                         .filter(Objects::nonNull)
                         .filter(FriendRequest::isPending)
                         .collect(Collectors.toList());
    }
    
    /**
     * Get sent friend requests by user
     */
    public List<FriendRequest> getSentRequests(String username) {
        List<String> requestIds = sentRequests.getOrDefault(username, Collections.emptyList());
        return requestIds.stream()
                         .map(friendRequests::get)
                         .filter(Objects::nonNull)
                         .filter(FriendRequest::isPending)
                         .collect(Collectors.toList());
    }
    
    /**
     * Check if there's a pending request between users
     */
    private boolean hasPendingRequest(String sender, String receiver) {
        List<String> sent = sentRequests.getOrDefault(sender, Collections.emptyList());
        for (String requestId : sent) {
            FriendRequest req = friendRequests.get(requestId);
            if (req != null && req.isPending() && 
                req.getSenderUsername().equals(sender) && 
                req.getReceiverUsername().equals(receiver)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Remove friendship
     */
    public synchronized boolean removeFriend(String username1, String username2) {
        boolean removed = false;
        
        Set<String> friends1 = friendships.get(username1);
        if (friends1 != null) {
            removed = friends1.remove(username2);
        }
        
        Set<String> friends2 = friendships.get(username2);
        if (friends2 != null) {
            friends2.remove(username1);
        }
        
        if (removed) {
            saveData();
            System.out.println("Friendship removed: " + username1 + " <-> " + username2);
        }
        
        return removed;
    }
    
    /**
     * Save data to disk
     */
    private void saveData() {
        try (ObjectOutputStream oos1 = new ObjectOutputStream(new FileOutputStream(FRIENDS_DB_FILE))) {
            oos1.writeObject(friendships);
        } catch (IOException e) {
            System.err.println("Error saving friendships: " + e.getMessage());
        }
        
        try (ObjectOutputStream oos2 = new ObjectOutputStream(new FileOutputStream(REQUESTS_DB_FILE))) {
            oos2.writeObject(friendRequests);
            oos2.writeObject(pendingRequests);
            oos2.writeObject(sentRequests);
        } catch (IOException e) {
            System.err.println("Error saving friend requests: " + e.getMessage());
        }
    }
    
    /**
     * Load data from disk
     */
    @SuppressWarnings("unchecked")
    private void loadData() {
        // Load friendships
        File friendsFile = new File(FRIENDS_DB_FILE);
        if (friendsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(friendsFile))) {
                friendships = (Map<String, Set<String>>) ois.readObject();
                System.out.println("Loaded " + friendships.size() + " user friendships");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error loading friendships: " + e.getMessage());
            }
        }
        
        // Load friend requests
        File requestsFile = new File(REQUESTS_DB_FILE);
        if (requestsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(requestsFile))) {
                friendRequests = (Map<String, FriendRequest>) ois.readObject();
                pendingRequests = (Map<String, List<String>>) ois.readObject();
                sentRequests = (Map<String, List<String>>) ois.readObject();
                System.out.println("Loaded " + friendRequests.size() + " friend requests");
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error loading friend requests: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get friendship statistics
     */
    public String getStats() {
        int totalFriendships = friendships.values().stream()
                                          .mapToInt(Set::size)
                                          .sum() / 2; // Divide by 2 because each friendship is counted twice
        int pendingRequestsCount = friendRequests.values().stream()
                                                  .filter(FriendRequest::isPending)
                                                  .mapToInt(r -> 1)
                                                  .sum();
        
        return String.format("Friendships: %d, Pending Requests: %d", 
                           totalFriendships, pendingRequestsCount);
    }
}
