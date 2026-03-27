package com.example.keycloakidentity.repository;

import com.example.keycloakidentity.domain.User;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * In-memory repository for {@link User} domain objects.
 *
 * <p>This repository uses a {@link ConcurrentHashMap} as the backing store, which is
 * thread-safe for concurrent read/write operations. In a production application, this
 * would be replaced with a JPA repository backed by a real database (e.g., PostgreSQL).
 *
 * <p><b>Why in-memory for this educational project?</b>
 * This project focuses on Keycloak identity integration — not database persistence.
 * Using an in-memory store keeps the code simple and removes the need for a database
 * dependency, letting learners focus on the authentication and authorization flow.
 *
 * <p><b>Thread safety:</b>
 * {@link ConcurrentHashMap} and {@link AtomicLong} are used to ensure this repository
 * is safe for use in a multi-threaded Spring Boot application where multiple requests
 * can be handled concurrently.
 *
 * <p>The repository is pre-seeded with a few sample users on initialization.
 */
@Repository
public class UserRepository {

    /**
     * The backing store: maps user ID → User object.
     * ConcurrentHashMap is used instead of HashMap for thread safety.
     */
    private final Map<Long, User> store = new ConcurrentHashMap<>();

    /**
     * Auto-incrementing ID counter. AtomicLong is used to generate unique IDs
     * in a thread-safe manner (no synchronized block needed).
     */
    private final AtomicLong idCounter = new AtomicLong(1);

    /**
     * Initializes the repository with pre-seeded demo users.
     * These simulate existing users that might have been synced from Keycloak.
     */
    public UserRepository() {
        // Seed a few demo users at startup
        Instant now = Instant.now();

        // Admin user — has ADMIN role, linked to a fictional Keycloak UUID
        User admin = new User(
                idCounter.getAndIncrement(),
                "kcid-admin-0001",
                "Alice Admin",
                "alice@example.com",
                "ADMIN",
                true,
                now,
                now
        );
        store.put(admin.getId(), admin);

        // Regular user — has USER role
        User user1 = new User(
                idCounter.getAndIncrement(),
                "kcid-user-0002",
                "Bob Reader",
                "bob@example.com",
                "USER",
                true,
                now,
                now
        );
        store.put(user1.getId(), user1);

        // Another regular user
        User user2 = new User(
                idCounter.getAndIncrement(),
                "kcid-user-0003",
                "Carol Viewer",
                "carol@example.com",
                "USER",
                true,
                now,
                now
        );
        store.put(user2.getId(), user2);

        // Inactive user — demonstrates the active/inactive status feature
        User inactive = new User(
                idCounter.getAndIncrement(),
                null, // not linked to Keycloak yet
                "Dave Pending",
                "dave@example.com",
                "USER",
                false,
                now,
                now
        );
        store.put(inactive.getId(), inactive);
    }

    /**
     * Returns all users in the repository.
     *
     * @return a new list containing all users; never null, may be empty
     */
    public List<User> findAll() {
        // Return a new ArrayList to prevent callers from mutating the store
        return new ArrayList<>(store.values());
    }

    /**
     * Finds a user by their application-internal ID.
     *
     * @param id the user's application ID
     * @return an Optional containing the user if found, or empty if not found
     */
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * Finds a user by their Keycloak UUID (the {@code sub} claim from the JWT).
     *
     * <p>This is used in the {@code /api/users/me} endpoint: the caller's Keycloak UUID
     * is extracted from the JWT and looked up here to find their application profile.
     *
     * @param keycloakId the Keycloak user UUID from the JWT's {@code sub} claim
     * @return an Optional containing the user if found, or empty if not found
     */
    public Optional<User> findByKeycloakId(String keycloakId) {
        // Stream through all users and find the one with a matching Keycloak ID
        return store.values().stream()
                .filter(u -> keycloakId.equals(u.getKeycloakId()))
                .findFirst();
    }

    /**
     * Returns all users with the given role.
     *
     * @param role the role to filter by (e.g., "ADMIN" or "USER")
     * @return a list of users with the given role; may be empty
     */
    public List<User> findByRole(String role) {
        return store.values().stream()
                .filter(u -> role.equalsIgnoreCase(u.getRole()))
                .collect(Collectors.toList());
    }

    /**
     * Saves a new user to the repository.
     *
     * <p>Assigns a new auto-generated ID and sets the creation timestamp
     * before persisting. This mirrors what a JPA repository would do automatically.
     *
     * @param user the user to save (must not have an ID — it will be assigned here)
     * @return the saved user with its assigned ID and timestamps
     */
    public User save(User user) {
        // Assign a unique ID using the atomic counter
        long newId = idCounter.getAndIncrement();
        user.setId(newId);

        // Set creation and modification timestamps
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // Default to active unless explicitly set to false
        if (!user.isActive()) {
            // Keep whatever active status was set by the caller
        }

        store.put(newId, user);
        return user;
    }

    /**
     * Updates an existing user in the repository.
     *
     * <p>Updates the {@code updatedAt} timestamp automatically.
     *
     * @param user the user object with updated field values (must have a valid ID)
     * @return the updated user
     */
    public User update(User user) {
        // Refresh the last-modified timestamp
        user.setUpdatedAt(Instant.now());
        store.put(user.getId(), user);
        return user;
    }

    /**
     * Deletes a user by their application-internal ID.
     *
     * @param id the ID of the user to delete
     * @return {@code true} if the user was removed, {@code false} if no user had that ID
     */
    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    /**
     * Returns the total count of users in the repository.
     *
     * @return the number of users currently stored
     */
    public int count() {
        return store.size();
    }
}
