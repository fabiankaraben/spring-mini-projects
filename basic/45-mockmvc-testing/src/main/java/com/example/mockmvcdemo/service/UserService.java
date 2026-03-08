package com.example.mockmvcdemo.service;

import com.example.mockmvcdemo.model.User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to handle User operations.
 * In a real application, this would talk to a repository.
 */
@Service
public class UserService {

    private final List<User> users = new ArrayList<>();

    public UserService() {
        // Initialize with dummy data
        users.add(new User(1L, "Alice", "alice@example.com"));
        users.add(new User(2L, "Bob", "bob@example.com"));
    }

    public List<User> getAllUsers() {
        return users;
    }

    public Optional<User> getUserById(Long id) {
        return users.stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    public User createUser(User user) {
        // Simple ID generation strategy for demo
        long newId = users.stream().mapToLong(User::getId).max().orElse(0) + 1;
        user.setId(newId);
        users.add(user);
        return user;
    }

    public boolean deleteUser(Long id) {
        return users.removeIf(u -> u.getId().equals(id));
    }
}
