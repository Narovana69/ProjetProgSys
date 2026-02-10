package com.reseau.client;

/**
 * CallState - Enum representing the state of a video call
 */
public enum CallState {
    IDLE("Idle"),
    RINGING("Ringing"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    ON_HOLD("On Hold"),
    ENDING("Ending"),
    ENDED("Ended"),
    FAILED("Failed");
    
    private final String description;
    
    CallState(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
