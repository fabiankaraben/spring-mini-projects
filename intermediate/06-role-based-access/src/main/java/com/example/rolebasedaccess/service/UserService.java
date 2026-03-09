package com.example.rolebasedaccess.service;

import com.example.rolebasedaccess.domain.Role;
import com.example.rolebasedaccess.domain.User;
import com.example.rolebasedaccess.dto.RegisterRequest;
import com.example.rolebasedaccess.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for user account management.
 *
 * <h2>Key educational focus: @PreAuthorize on service methods</h2>
 * <p>This service demonstrates placing {@code @PreAuthorize} annotations
 * directly on <em>service</em> methods rather than (or in addition to)
 * controller methods. This is the preferred pattern for reusable business
 * logic because:</p>
 * <ul>
 *   <li>The security rule is enforced wherever the method is called, even if
 *       the caller is another service or a scheduled task – not just HTTP.</li>
 *   <li>It keeps controllers thin; the business rule lives next to the
 *       business logic it protects.</li>
 * </ul>
 *
 * <h2>SpEL expressions used</h2>
 * <ul>
 *   <li>{@code hasRole('ADMIN')} – caller must have authority {@code ROLE_ADMIN}.</li>
 *   <li>{@code hasAnyRole('ADMIN', 'MODERATOR')} – caller must have either
 *       {@code ROLE_ADMIN} or {@code ROLE_MODERATOR}.</li>
 * </ul>
 *
 * <h2>Method security prerequisite</h2>
 * <p>{@code @PreAuthorize} only works when
 * {@code @EnableMethodSecurity} is present on a {@code @Configuration} class.
 * See {@code SecurityConfig} for this annotation.</p>
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * BCrypt password encoder injected here so tests can substitute a
     * {@code NoOpPasswordEncoder} for speed without BCrypt overhead.
     */
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a new user account.
     *
     * <p>This method is intentionally <strong>not</strong> annotated with
     * {@code @PreAuthorize} because registration must be accessible without
     * authentication (it is the entry point to the system).</p>
     *
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Check that the username is not already taken.</li>
     *   <li>Encode the plain-text password with BCrypt.</li>
     *   <li>Determine the role: use the requested role if valid, else default to
     *       {@code ROLE_USER}.</li>
     *   <li>Persist the new {@link User} entity.</li>
     * </ol>
     *
     * @param request DTO with the desired username, password and optional role
     * @return the persisted {@link User} entity (with its generated {@code id})
     * @throws IllegalArgumentException if the username is already taken
     */
    public User registerUser(RegisterRequest request) {
        // Enforce the "unique username" business rule at the service level.
        // The DB has a unique constraint too, but checking here lets us return
        // a friendly 409 Conflict instead of a raw DataIntegrityViolationException.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        // Encode the password with BCrypt before storing.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Parse the requested role, defaulting to ROLE_USER if not provided or invalid.
        Role role = parseRole(request.getRole());

        User user = new User(request.getUsername(), encodedPassword, role);
        return userRepository.save(user);
    }

    /**
     * Returns all registered users.
     *
     * <p><strong>Access rule:</strong> only callers with {@code ROLE_ADMIN} may
     * list all users. A regular user or moderator calling this will receive
     * {@code 403 Forbidden} (enforced by Spring Security before the method body runs).</p>
     *
     * @return list of all {@link User} entities
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getAllUsers() {
        // The @PreAuthorize check runs before this line.
        // If the caller lacks ROLE_ADMIN, an AccessDeniedException is thrown
        // by the Spring Security proxy before we reach here.
        return userRepository.findAll();
    }

    /**
     * Finds a user by ID.
     *
     * <p><strong>Access rule:</strong> accessible to ADMIN and MODERATOR roles.
     * Regular users cannot look up arbitrary user accounts.</p>
     *
     * @param id the user's primary key
     * @return the found {@link User} entity
     * @throws IllegalArgumentException if no user with that ID exists
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    /**
     * Updates the role of an existing user.
     *
     * <p><strong>Access rule:</strong> only {@code ROLE_ADMIN} may change roles.
     * This is a privileged operation that should never be accessible to
     * ordinary users or even moderators.</p>
     *
     * @param id      the user's primary key
     * @param newRole the new role to assign (e.g. {@code "ROLE_MODERATOR"})
     * @return the updated {@link User} entity
     * @throws IllegalArgumentException if no user with that ID exists, or the role string is invalid
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUserRole(Long id, String newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        // Parse the new role; throw if it is not a valid enum constant.
        Role role;
        try {
            role = Role.valueOf(newRole);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid role: " + newRole +
                    ". Valid values are: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN");
        }

        user.setRole(role);
        return userRepository.save(user);
    }

    /**
     * Deletes a user account.
     *
     * <p><strong>Access rule:</strong> only {@code ROLE_ADMIN} may delete users.</p>
     *
     * @param id the user's primary key
     * @throws IllegalArgumentException if no user with that ID exists
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Parses a role string into a {@link Role} enum constant, defaulting to
     * {@link Role#ROLE_USER} when the input is null, blank, or unrecognised.
     *
     * @param roleString the raw role string (may be null)
     * @return the parsed role, or {@code ROLE_USER} as the default
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
