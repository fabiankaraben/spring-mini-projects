package com.example.jwtgeneration.service;

import com.example.jwtgeneration.domain.Role;
import com.example.jwtgeneration.domain.User;
import com.example.jwtgeneration.dto.RegisterRequest;
import com.example.jwtgeneration.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that manages {@link User} lifecycle operations.
 *
 * <p>Responsibilities of this class:
 * <ul>
 *   <li><strong>Registration</strong> – validates that the username is not
 *       already taken, encodes the plain-text password with BCrypt, assigns
 *       the default {@code ROLE_USER} role, and persists the new user.</li>
 * </ul>
 *
 * <p>Authentication (password verification) is intentionally <em>not</em>
 * handled here. Spring Security's {@code AuthenticationManager} delegates that
 * to {@code UserDetailsServiceImpl}, which loads the user from the database and
 * lets Spring's {@code DaoAuthenticationProvider} compare the submitted password
 * against the stored BCrypt hash.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    /**
     * {@link PasswordEncoder} bean configured as BCrypt in {@code SecurityConfig}.
     * BCrypt is the recommended encoder because it is adaptive (cost factor can
     * be increased over time) and automatically includes a random salt.
     */
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user account with the default {@code ROLE_USER} role.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>Check that no user with the same username already exists.</li>
     *   <li>Encode the plain-text password with BCrypt.</li>
     *   <li>Persist the new {@link User} entity.</li>
     * </ol>
     *
     * @param request DTO containing the desired username and plain-text password
     * @return the persisted {@link User} (with ID populated by JPA)
     * @throws IllegalArgumentException if the username is already taken
     */
    @Transactional
    public User registerUser(RegisterRequest request) {
        // Reject duplicate usernames before hitting the unique DB constraint;
        // this produces a friendlier error message for the client.
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                    "Username '" + request.getUsername() + "' is already taken");
        }

        // Encode the password – NEVER store plain-text passwords.
        // BCrypt generates a random salt and embeds it in the hash string, so
        // two calls with the same password produce different hashes. That is
        // intentional and is a security feature.
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // All new self-registered users receive the standard USER role.
        User user = new User(request.getUsername(), encodedPassword, Role.ROLE_USER);

        return userRepository.save(user);
    }
}
