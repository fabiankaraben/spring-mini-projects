package com.example.keycloakidentity.service;

import com.example.keycloakidentity.domain.User;
import com.example.keycloakidentity.dto.CreateUserRequest;
import com.example.keycloakidentity.dto.UpdateUserRequest;
import com.example.keycloakidentity.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for the Users domain.
 *
 * <p>This class implements the business logic for managing application user records.
 * It sits between the REST controller (HTTP layer) and the repository (data layer),
 * following the classic three-tier architecture:
 * <pre>
 *   Controller → Service → Repository
 * </pre>
 *
 * <p><b>Responsibilities of this service:</b>
 * <ul>
 *   <li>Map incoming DTOs ({@link CreateUserRequest}, {@link UpdateUserRequest}) to
 *       domain objects ({@link User}).</li>
 *   <li>Apply business rules (e.g., defaulting {@code active = true} on creation).</li>
 *   <li>Look up users by Keycloak ID for the "current user" feature.</li>
 *   <li>Delegate all persistence operations to the {@link UserRepository}.</li>
 * </ul>
 *
 * <p><b>Design note — separation of concerns:</b>
 * Authentication and token validation are handled entirely by Spring Security (configured
 * in {@link com.example.keycloakidentity.config.SecurityConfig}). By the time a request
 * reaches this service, the caller's identity is already verified. The service only handles
 * the application-level logic of the user resource.
 */
@Service
public class UserService {

    /**
     * The data access layer. Constructor injection is preferred over field injection
     * because it makes dependencies explicit and allows the service to be tested
     * without loading a Spring context.
     */
    private final UserRepository userRepository;

    /**
     * Constructs the service with its required repository dependency.
     *
     * @param userRepository the in-memory user data store
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns all users in the application.
     *
     * @return a list of all users; never null, may be empty
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Finds a user by their application-internal ID.
     *
     * @param id the application-level user ID
     * @return an {@link Optional} containing the user if found, or empty if not found
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Finds a user by their Keycloak UUID.
     *
     * <p>This is the key integration point between Keycloak identity and the
     * application's own user data. The Keycloak UUID comes from the JWT's {@code sub}
     * claim and is used to look up the corresponding application user profile.
     *
     * <p>Typical use case: the {@code GET /api/users/me} endpoint calls this method
     * with the authenticated caller's Keycloak UUID to return their own profile.
     *
     * @param keycloakId the Keycloak user UUID (the {@code sub} JWT claim)
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    public Optional<User> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    /**
     * Returns all users with the specified role.
     *
     * @param role the role to filter by (e.g., "USER" or "ADMIN")
     * @return a list of users with the given role; may be empty
     */
    public List<User> getUsersByRole(String role) {
        return userRepository.findByRole(role);
    }

    /**
     * Creates a new user from the provided request DTO.
     *
     * <p>Business rules applied:
     * <ul>
     *   <li>A new {@link User} domain object is constructed from the DTO fields.</li>
     *   <li>{@code active} is defaulted to {@code true} — new users are active by default.</li>
     *   <li>The repository assigns the ID and timestamps automatically.</li>
     * </ul>
     *
     * @param request the validated create request DTO
     * @return the newly created user with its assigned ID and timestamps
     */
    public User createUser(CreateUserRequest request) {
        // Map the request DTO to a domain object.
        // The controller/API layer should never send domain objects directly —
        // the DTO provides a stable contract for what the client can specify.
        User user = new User();
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setKeycloakId(request.getKeycloakId()); // may be null

        // New users default to active — they can be deactivated via an update
        user.setActive(true);

        // Delegate persistence to the repository (assigns ID and timestamps)
        return userRepository.save(user);
    }

    /**
     * Partially updates an existing user.
     *
     * <p>Only non-null fields in the {@link UpdateUserRequest} are applied to the
     * existing user record. This implements patch-like semantics: a client can update
     * only the email without resending displayName, role, etc.
     *
     * @param id      the ID of the user to update
     * @param request the update request DTO (only non-null fields are applied)
     * @return an {@link Optional} containing the updated user, or empty if not found
     */
    public Optional<User> updateUser(Long id, UpdateUserRequest request) {
        // Find the existing user — return empty if no user has this ID
        return userRepository.findById(id).map(existing -> {

            // Apply only the non-null fields from the update request (partial update)
            if (request.getDisplayName() != null) {
                existing.setDisplayName(request.getDisplayName());
            }
            if (request.getEmail() != null) {
                existing.setEmail(request.getEmail());
            }
            if (request.getRole() != null) {
                existing.setRole(request.getRole());
            }
            if (request.getActive() != null) {
                // Boxed Boolean — null means "don't change", non-null means "set to this value"
                existing.setActive(request.getActive());
            }

            // Persist the updated user (repository sets updatedAt)
            return userRepository.update(existing);
        });
    }

    /**
     * Deletes the user with the given ID.
     *
     * @param id the ID of the user to delete
     * @return {@code true} if the user was deleted, {@code false} if no user had that ID
     */
    public boolean deleteUser(Long id) {
        return userRepository.deleteById(id);
    }

    /**
     * Returns the total count of users in the application.
     *
     * @return the total number of users
     */
    public int getUserCount() {
        return userRepository.count();
    }
}
