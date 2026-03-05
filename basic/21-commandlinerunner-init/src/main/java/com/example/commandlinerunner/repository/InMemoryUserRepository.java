package com.example.commandlinerunner.repository;

import com.example.commandlinerunner.model.User;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An in-memory repository to manage Users.
 * This simulates a database so we don't need external Docker dependencies.
 */
@Repository
public class InMemoryUserRepository implements UserRepository {

    private final List<User> users = new ArrayList<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * Saves a new user to the repository or updates an existing one.
     */
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(String.valueOf(idGenerator.getAndIncrement()));
            users.add(user);
        } else {
            // Simulated update
            findById(user.getId()).ifPresent(existing -> {
                existing.setName(user.getName());
                existing.setEmail(user.getEmail());
            });
            if (findById(user.getId()).isEmpty()) {
                users.add(user);
            }
        }
        return user;
    }

    /**
     * Returns all stored users.
     */
    public List<User> findAll() {
        return new ArrayList<>(users);
    }

    /**
     * Finds a user by ID.
     */
    public Optional<User> findById(String id) {
        return users.stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    /**
     * Clears all users from the repository. Very useful for testing.
     */
    public void deleteAll() {
        users.clear();
        idGenerator.set(1);
    }
}
