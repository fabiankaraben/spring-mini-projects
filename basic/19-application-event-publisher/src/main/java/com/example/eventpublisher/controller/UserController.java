package com.example.eventpublisher.controller;

import com.example.eventpublisher.listener.UserRegistrationListener;
import com.example.eventpublisher.publisher.UserRegistrationPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Basic REST API controller exposing HTTP endpoints to interact with the events
 * logic.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRegistrationPublisher publisher;
    private final UserRegistrationListener listener;

    public UserController(UserRegistrationPublisher publisher, UserRegistrationListener listener) {
        this.publisher = publisher;
        this.listener = listener;
    }

    /**
     * Endpoint to simulate registering a user which internally triggers an event.
     * 
     * @param username The path variable to publish
     * @return Confirmation message
     */
    @PostMapping("/{username}")
    public ResponseEntity<String> registerUser(@PathVariable String username) {

        // Let's assume some DB save operation takes place here...
        // ... then we publish the event confirming completion
        publisher.publishUserRegistration(username);

        return ResponseEntity
                .ok("Successfully handled initial step and published UserRegistrationEvent for '" + username + "'.");
    }

    /**
     * Endpoint to check the downstream processing side-effects from the events
     * listener class.
     * 
     * @return A list of successfully handled usernames
     */
    @GetMapping
    public ResponseEntity<List<String>> fetchProcessedUsers() {
        return ResponseEntity.ok(listener.getRegisteredUsernames());
    }
}
