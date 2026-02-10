package com.reseau.client;

import java.util.concurrent.atomic.AtomicReference;

/**
 * VideoCallManager - Manages the state of video calls with strict state control
 * Ensures only ONE video call window is active at a time
 * Prevents multiple simultaneous calls and handles cleanup properly
 */
public class VideoCallManager {
    private static final VideoCallManager instance = new VideoCallManager();
    
    private final AtomicReference<VideoCallWindow> activeCallWindow = new AtomicReference<>(null);
    private final AtomicReference<CallState> callState = new AtomicReference<>(CallState.IDLE);
    
    private VideoCallManager() {
    }
    
    public static VideoCallManager getInstance() {
        return instance;
    }
    
    /**
     * Try to start a new video call
     * Only succeeds if no other call is active
     * @param window The video call window to activate
     * @return true if call was started, false if another call is already active
     */
    public synchronized boolean startCall(VideoCallWindow window) {
        // Check current state
        CallState currentState = callState.get();
        if (currentState != CallState.IDLE && currentState != CallState.ENDED && currentState != CallState.FAILED) {
            System.out.println("❌ Cannot start call: Call is already in " + currentState.getDescription() + " state");
            return false;
        }
        
        // Check if window already exists and is showing
        VideoCallWindow existing = activeCallWindow.get();
        if (existing != null) {
            try {
                if (existing.getStage().isShowing()) {
                    System.out.println("❌ Cannot start call: Another call window is already showing");
                    return false;
                } else {
                    // Old window closed, clean it up
                    cleanupCall(existing);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error checking existing window: " + e.getMessage());
                cleanupCall(existing);
            }
        }
        
        // Register the new call
        activeCallWindow.set(window);
        callState.set(CallState.CONNECTING);
        
        // Set callback to clean up when window closes
        window.setOnWindowClosed(() -> {
            // No nested synchronization, just atomically update references
            if (activeCallWindow.compareAndSet(window, null)) {
                callState.set(CallState.IDLE);
                System.out.println("✅ Video call closed and manager reset");
            }
        });
        
        System.out.println("📞 Video call starting...");
        return true;
    }
    
    /**
     * Notify that the call is now connected
     */
    public synchronized void callConnected() {
        if (callState.compareAndSet(CallState.CONNECTING, CallState.CONNECTED)) {
            System.out.println("✅ Video call connected");
        }
    }
    
    /**
     * Notify that the call failed
     */
    public synchronized void callFailed(String reason) {
        callState.set(CallState.FAILED);
        System.err.println("❌ Video call failed: " + reason);
        
        VideoCallWindow window = activeCallWindow.getAndSet(null);
        if (window != null) {
            try {
                window.disconnect();
            } catch (Exception e) {
                System.err.println("Error disconnecting failed call: " + e.getMessage());
            }
        }
    }
    
    /**
     * Check if a call is currently active
     */
    public boolean isCallActive() {
        VideoCallWindow window = activeCallWindow.get();
        if (window == null) {
            return false;
        }
        
        try {
            if (!window.getStage().isShowing()) {
                // Window closed but reference still exists, clean up
                cleanupCall(window);
                return false;
            }
            
            CallState state = callState.get();
            return state == CallState.CONNECTING || state == CallState.CONNECTED;
            
        } catch (Exception e) {
            System.err.println("Error checking call status: " + e.getMessage());
            cleanupCall(window);
            return false;
        }
    }
    
    /**
     * Get the active call window, or null if no call is active
     */
    public VideoCallWindow getActiveCall() {
        if (isCallActive()) {
            return activeCallWindow.get();
        }
        return null;
    }
    
    /**
     * Get current call state
     */
    public CallState getCallState() {
        return callState.get();
    }
    
    /**
     * End the current call gracefully
     */
    public synchronized void endCall() {
        VideoCallWindow window = activeCallWindow.getAndSet(null);
        if (window != null) {
            callState.set(CallState.ENDING);
            try {
                window.disconnect();
                System.out.println("📞 Video call ended");
            } catch (Exception e) {
                System.err.println("Error ending call: " + e.getMessage());
            } finally {
                callState.set(CallState.IDLE);
            }
        }
    }
    
    /**
     * Force cleanup of a call window
     */
    private void cleanupCall(VideoCallWindow window) {
        try {
            window.disconnect();
        } catch (Exception e) {
            System.err.println("Error cleaning up call: " + e.getMessage());
        }
        activeCallWindow.compareAndSet(window, null);
        callState.set(CallState.IDLE);
    }
    
    /**
     * Reset manager to initial state (use with caution)
     */
    public synchronized void reset() {
        VideoCallWindow window = activeCallWindow.getAndSet(null);
        if (window != null) {
            cleanupCall(window);
        }
        callState.set(CallState.IDLE);
        System.out.println("⚠️ VideoCallManager reset");
    }
}
