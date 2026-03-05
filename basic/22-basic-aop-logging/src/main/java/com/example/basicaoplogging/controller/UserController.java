package com.example.basicaoplogging.controller;

import com.example.basicaoplogging.model.User;
import com.example.basicaoplogging.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Endpoint for User operations.
 * Requests hitting these endpoints will trigger the associated service methods,
 * enabling AOP logging for those service method calls.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Handle GET request to retrieve all users.
     */
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    /**
     * Handle GET request to retrieve a single user by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Handle POST request to add a new user.
     * Consumes JSON Payload e.g. {"name": "Test User"}
     */
    @PostMapping
    public User createUser(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        return userService.createUser(name);
    }

    /**
     * Handle DELETE request to remove a user by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (userService.deleteUser(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Handle GET request to intentionally trigger an exception in the Service
     * layer.
     * Useful for seeing the @AfterThrowing aspect logging behavior.
     */
    @GetMapping("/error")
    public ResponseEntity<String> triggerError() {
        userService.throwExceptionMethod("Intentional Exception from Controller");
        return ResponseEntity.ok("This will not be reached");
    }
}
