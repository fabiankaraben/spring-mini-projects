package com.example.jwtvalidation.service;

import com.example.jwtvalidation.domain.Role;
import com.example.jwtvalidation.domain.User;
import com.example.jwtvalidation.dto.RegisterRequest;
import com.example.jwtvalidation.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Application service for user account management.
 *
 * <p>Handles the registration flow: validate that the username is not already
 * taken, encode the plain-text password with BCrypt, and persist the new
 * {@link User} entity.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Enforce the "unique username" business rule at the service level
 *       (the database has a unique constraint too, but checking here lets us
 *       return a friendly {@code 409 Conflict} rather than a raw SQL error).</li>
 *   <li>Encode passwords using the injected {@link PasswordEncoder} (BCrypt)
 *       before they reach the database layer.</li>
 *   <li>Assign the default {@link Role#ROLE_USER} to every new user.</li>
 * </ul>
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * BCrypt password encoder. Injected here instead of called directly so
     * that tests can substitute a {@code NoOpPasswordEncoder} for speed.
     */
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user account.
     *
     * <p>Steps performed:</p>
     * <ol>
     *   <li>Check that the requested username is not already taken.</li>
     *   <li>Encode the plain-text password with BCrypt.</li>
     *   <li>Persist the new {@link User} entity with the {@code ROLE_USER} role.</li>
     * </ol>
     *
     * @param request DTO with the desired username and plain-text password
     * @return the persisted {@link User} entity (with its generated {@code id})
     * @throws IllegalArgumentException if the username is already taken
     */
    public User registerUser(RegisterRequest request) {
        // Check for duplicate username before attempting to persist.
        // This produces a clear error message and avoids a DataIntegrityViolationException.
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        // Encode the password with BCrypt.
        // BCrypt automatically generates a random salt and embeds it in the hash,
        // so identical passwords produce different hashes on each call.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // All self-registered users get the standard ROLE_USER authority.
        User user = new User(request.getUsername(), encodedPassword, Role.ROLE_USER);

        return userRepository.save(user);
    }
}
