package com.example.eventpublisher.publisher;

import com.example.eventpublisher.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Component responsible for building and publishing the custom Spring
 * application events.
 * It uses Spring's injected ApplicationEventPublisher to disperse the event
 * contextually.
 */
@Component
public class UserRegistrationPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationPublisher.class);

    // Core Spring interface to publish events
    private final ApplicationEventPublisher applicationEventPublisher;

    public UserRegistrationPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Call this method whenever a user registration action takes place.
     * The method instantiates our custom UserRegisteredEvent and fires it on the
     * ApplicationContext.
     * 
     * @param username The username that was processed
     */
    public void publishUserRegistration(String username) {
        log.info("Publishing UserRegisteredEvent for username: {}", username);

        // 1. Create the event object
        UserRegisteredEvent customEvent = new UserRegisteredEvent(username);

        // 2. Publish it synchronously (by default) to all registered listeners
        applicationEventPublisher.publishEvent(customEvent);
    }
}
