package com.example.commandlinerunner.repository;

import com.example.commandlinerunner.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Interface representing User database operations.
 */
public interface UserRepository {
    User save(User user);
    List<User> findAll();
    Optional<User> findById(String id);
    void deleteAll();
}
