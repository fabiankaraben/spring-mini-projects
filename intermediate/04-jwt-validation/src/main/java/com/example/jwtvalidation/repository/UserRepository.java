package com.example.jwtvalidation.repository;

import com.example.jwtvalidation.domain.User;
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
}
