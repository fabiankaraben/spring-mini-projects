package com.example.rolebasedaccess.repository;

import com.example.rolebasedaccess.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring Data automatically generates the implementation at startup by
 * inspecting the method names and generating the appropriate JPQL queries.
 * No implementation class is needed.</p>
 *
 * <h2>Why Optional?</h2>
 * <p>Returning {@link Optional} instead of {@code null} forces callers to
 * handle the "not found" case explicitly, reducing the chance of a
 * {@code NullPointerException}. In {@code UserDetailsServiceImpl} we use
 * {@code orElseThrow()} to convert a missing user into a
 * {@code UsernameNotFoundException}.</p>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username.
     *
     * <p>Spring Data translates this method name into:
     * {@code SELECT u FROM User u WHERE u.username = ?1}</p>
     *
     * @param username the login name to look up
     * @return an {@link Optional} containing the matching user, or
     *         {@link Optional#empty()} if no user exists with that username
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks whether a user with the given username already exists.
     *
     * <p>Spring Data translates this to:
     * {@code SELECT COUNT(u) > 0 FROM User u WHERE u.username = ?1}</p>
     *
     * <p>Using this instead of {@code findByUsername(...).isPresent()} avoids
     * loading the full entity just to check existence.</p>
     *
     * @param username the login name to check
     * @return {@code true} if a user with that username exists
     */
    boolean existsByUsername(String username);
}
