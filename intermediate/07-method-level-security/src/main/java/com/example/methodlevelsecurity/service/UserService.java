package com.example.methodlevelsecurity.service;

import com.example.methodlevelsecurity.domain.Role;
import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.dto.RegisterRequest;
import com.example.methodlevelsecurity.repository.UserRepository;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for user account management.
 *
 * <h2>Educational focus: @Secured and @PreAuthorize on service methods</h2>
 * <p>This service demonstrates two complementary method-level security annotations:</p>
 *
 * <h3>{@code @Secured}</h3>
 * <p>The older, simpler annotation. Only supports role-name strings; does not support
 * SpEL expressions. Enabled via {@code securedEnabled = true} in
 * {@link com.example.methodlevelsecurity.config.SecurityConfig}.
 * Example: {@code @Secured("ROLE_ADMIN")} is equivalent to
 * {@code @PreAuthorize("hasRole('ADMIN')")}.</p>
 *
 * <h3>{@code @PreAuthorize}</h3>
 * <p>The modern, powerful annotation. Accepts any SpEL expression evaluated
 * against the current {@link org.springframework.security.core.Authentication}.
 * Example expressions:</p>
 * <ul>
 *   <li>{@code hasRole('ADMIN')} – single role check.</li>
 *   <li>{@code hasAnyRole('ADMIN', 'MODERATOR')} – any of the listed roles.</li>
 *   <li>{@code isAuthenticated()} – any authenticated user.</li>
 *   <li>{@code authentication.name == 'alice'} – specific user.</li>
 * </ul>
 *
 * <h2>Why protect at the service layer?</h2>
 * <p>Placing security annotations on service methods (not just controllers) ensures
 * that access rules are enforced regardless of <em>how</em> the method is called:
 * via HTTP, a scheduled job, a messaging consumer, or another service.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Public (unauthenticated) operations ───────────────────────────────────

    /**
     * Registers a new user.
     *
     * <p>No security annotation because registration must be publicly accessible –
     * it is the entry point to the system.</p>
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Validate that the username is not already taken.</li>
     *   <li>Encode the plain-text password with BCrypt.</li>
     *   <li>Determine the role (defaults to {@code ROLE_USER}).</li>
     *   <li>Persist and return the new user.</li>
     * </ol>
     *
     * @param request DTO with username, password, and optional role
     * @return the persisted {@link User} entity
     * @throws IllegalArgumentException if the username is already taken
     */
    public User registerUser(RegisterRequest request) {
        // Check uniqueness at the service level for a friendly error message.
        // The DB unique constraint is a safety net for concurrent registrations.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        // Encode the password with BCrypt before storing – never store plain text.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Default to ROLE_USER if the role is missing, blank, or unrecognised.
        Role role = parseRole(request.getRole());

        User user = new User(request.getUsername(), encodedPassword, role);
        return userRepository.save(user);
    }

    // ── Admin-only operations ─────────────────────────────────────────────────

    /**
     * Returns all registered users.
     *
     * <p><strong>Security:</strong> {@code @Secured("ROLE_ADMIN")} demonstrates the
     * older-style annotation. It is equivalent to
     * {@code @PreAuthorize("hasRole('ADMIN')")} but does not support SpEL.
     * Only admins may list all users.</p>
     *
     * @return list of all {@link User} entities in the database
     */
    @Secured("ROLE_ADMIN")
    public List<User> getAllUsers() {
        // @Secured check runs before this line via a Spring Security CGLIB proxy.
        // If the caller lacks ROLE_ADMIN, AccessDeniedException is thrown here.
        return userRepository.findAll();
    }

    /**
     * Finds a user by primary key.
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize} with
     * {@code hasAnyRole('ADMIN', 'MODERATOR')} – admins and moderators may look up
     * any user; regular users cannot.</p>
     *
     * @param id the user's primary key
     * @return the found {@link User}
     * @throws IllegalArgumentException if no user with that ID exists
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with id: " + id));
    }

    /**
     * Updates the role of an existing user.
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize("hasRole('ADMIN')")} –
     * role changes are privileged and must be restricted to admins only.
     * A moderator or regular user calling this will receive {@code 403 Forbidden}.</p>
     *
     * @param id      the user's primary key
     * @param newRole the new role string (e.g. {@code "ROLE_MODERATOR"})
     * @return the updated {@link User}
     * @throws IllegalArgumentException if the user is not found or the role is invalid
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserRole(Long id, String newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with id: " + id));

        // Parse the new role – throw a descriptive error if it is not a valid enum name.
        Role role;
        try {
            role = Role.valueOf(newRole);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid role: " + newRole +
                    ". Valid values: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN");
        }

        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Deletes a user account.
     *
     * <p><strong>Security:</strong> {@code @Secured("ROLE_ADMIN")} – again using
     * the older annotation to show contrast with {@code @PreAuthorize}.</p>
     *
     * @param id the user's primary key
     * @throws IllegalArgumentException if no user with that ID exists
     */
    @Secured("ROLE_ADMIN")
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses a role string to a {@link Role} enum constant.
     * Returns {@link Role#ROLE_USER} if the string is null, blank, or unrecognised.
     *
     * @param roleString raw role string from the registration request (may be null)
     * @return the parsed role, or {@code ROLE_USER} as a safe default
     */
    private Role parseRole(String roleString) {
        if (roleString == null || roleString.isBlank()) {
            return Role.ROLE_USER;
        }
        try {
            return Role.valueOf(roleString.toUpperCase());
        } catch (IllegalArgumentException ex) {
            // Unrecognised role string – fall back to the safest default.
            return Role.ROLE_USER;
        }
    }
}
