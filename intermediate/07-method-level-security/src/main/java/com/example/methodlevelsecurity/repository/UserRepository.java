package com.example.methodlevelsecurity.repository;

import com.example.methodlevelsecurity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring Data auto-generates the implementation at startup based on the
 * method signatures defined here. No manual SQL or boilerplate code is required.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username.
     *
     * <p>Used by {@link com.example.methodlevelsecurity.security.UserDetailsServiceImpl}
     * to load the user during JWT authentication and by the service layer during
     * login and registration operations.</p>
     *
     * @param username the username to look up
     * @return an {@link Optional} containing the user, or empty if not found
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks whether a user with the given username already exists.
     *
     * <p>Used during registration to provide a user-friendly duplicate-username
     * error instead of letting a {@code DataIntegrityViolationException} bubble up.</p>
     *
     * @param username the username to check
     * @return {@code true} if a user with that username exists
     */
    boolean existsByUsername(String username);
}
