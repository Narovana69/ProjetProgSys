package com.reseau.common;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * FriendRequest - Represents a friend request between two users
 */
public class FriendRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String requestId;
    private String senderUsername;
    private String receiverUsername;
    private FriendRequestStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime respondedAt;
    
    public enum FriendRequestStatus {
        PENDING,    // En attente
        ACCEPTED,   // Acceptée
        REJECTED,   // Refusée
        CANCELLED   // Annulée par l'envoyeur
    }
    
    public FriendRequest(String senderUsername, String receiverUsername) {
        this.requestId = generateRequestId(senderUsername, receiverUsername);
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.status = FriendRequestStatus.PENDING;
        this.sentAt = LocalDateTime.now();
    }
    
    private String generateRequestId(String sender, String receiver) {
        return sender + "_to_" + receiver + "_" + System.currentTimeMillis();
    }
    
    public void accept() {
        this.status = FriendRequestStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }
    
    public void reject() {
        this.status = FriendRequestStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }
    
    public void cancel() {
        this.status = FriendRequestStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }
    
    // Getters
    public String getRequestId() { return requestId; }
    public String getSenderUsername() { return senderUsername; }
    public String getReceiverUsername() { return receiverUsername; }
    public FriendRequestStatus getStatus() { return status; }
    public LocalDateTime getSentAt() { return sentAt; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    
    public boolean isPending() {
        return status == FriendRequestStatus.PENDING;
    }
    
    public boolean isAccepted() {
        return status == FriendRequestStatus.ACCEPTED;
    }
    
    @Override
    public String toString() {
        return "FriendRequest{" +
                "from='" + senderUsername + '\'' +
                ", to='" + receiverUsername + '\'' +
                ", status=" + status +
                ", sentAt=" + sentAt +
                '}';
    }
}
