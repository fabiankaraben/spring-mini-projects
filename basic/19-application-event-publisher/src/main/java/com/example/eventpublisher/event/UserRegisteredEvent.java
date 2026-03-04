package com.example.eventpublisher.event;

/**
 * A simple POJO representing the event.
 * In Spring 4.2+, you don't need to extend ApplicationEvent.
 * The event holds domain data - in this case, the registered username.
 */
public class UserRegisteredEvent {

    // The informational payload of this event
    private final String username;

    public UserRegisteredEvent(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
