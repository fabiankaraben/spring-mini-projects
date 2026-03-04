package com.example.eventpublisher.listener;

import com.example.eventpublisher.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Component that listens for the UserRegisteredEvent published through Spring's
 * ApplicationContext.
 */
@Component
public class UserRegistrationListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationListener.class);

    // We are storing the parsed events in-memory to provide an easily verifiable
    // effect.
    // In a real application, you might use an in-memory database, cache, or
    // external service.
    private final List<String> registeredUsernames = new ArrayList<>();

    /**
     * The @EventListener annotation registers this method as an application event
     * listener.
     * The method argument type logically determines which event it is wired to
     * listen to.
     * 
     * @param event Contains the payload data to be consumed (the username in this
     *              case).
     */
    @EventListener
    public void handleUserRegistrationEvent(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent - proceeding to handle actions for: {}", event.getUsername());

        // Simulating the business logic execution driven by the event
        registeredUsernames.add(event.getUsername());
    }

    /**
     * Diagnostic method to inspect the state.
     * 
     * @return Current list of processed registered users.
     */
    public List<String> getRegisteredUsernames() {
        return registeredUsernames;
    }
}
