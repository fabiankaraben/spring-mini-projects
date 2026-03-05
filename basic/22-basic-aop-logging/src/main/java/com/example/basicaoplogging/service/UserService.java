package com.example.basicaoplogging.service;

import com.example.basicaoplogging.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This service class manages User operations.
 * Its public methods will be tracked by our AspectJ LoggingAspect.
 * 
 * We use an in-memory list as a mock repository for simplicity.
 */
@Service
public class UserService {

    private final List<User> users = new ArrayList<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    /**
     * Retrieves all users (mocked repository).
     * 
     * @return List of current users.
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    /**
     * Retrieves a single user by ID.
     * 
     * @param id The ID to look for.
     * @return The user, if it exists.
     */
    public Optional<User> getUserById(Long id) {
        return users.stream()
                .filter(u -> u.id().equals(id))
                .findFirst();
    }

    /**
     * Creates a new user and adds it to our managed list.
     * 
     * @param name The name of the new user.
     * @return The newly created User object.
     */
    public User createUser(String name) {
        // Simulating some processing time if needed, but not required
        User newUser = new User(idCounter.getAndIncrement(), name);
        users.add(newUser);
        return newUser;
    }

    /**
     * Deletes a user by its ID.
     * 
     * @param id The ID of the user to remove.
     * @return true if the user was found and removed, false otherwise.
     */
    public boolean deleteUser(Long id) {
        return users.removeIf(u -> u.id().equals(id));
    }

    /**
     * Simulates a method that throws an exception to demonstrate @AfterThrowing
     * aspect logging.
     * 
     * @param cause The message reason.
     */
    public void throwExceptionMethod(String cause) {
        throw new IllegalArgumentException("Simulated exception: " + cause);
    }
}
