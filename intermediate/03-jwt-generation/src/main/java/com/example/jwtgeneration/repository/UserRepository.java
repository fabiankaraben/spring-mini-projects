package com.example.jwtgeneration.repository;

import com.example.jwtgeneration.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring automatically generates the implementation at startup by scanning
 * the interface. No manual SQL or boilerplate DAO code is needed.
 *
 * <p>The only custom query needed here is a lookup by username, which is used by
 * both {@code UserDetailsServiceImpl} (Spring Security authentication) and
 * {@code UserService} (registration duplicate check).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username.
     *
     * <p>Spring Data derives the SQL {@code WHERE username = ?} clause from the
     * method name at compile time – no {@code @Query} annotation is required.
     *
     * @param username the login name to search for
     * @return an {@link Optional} containing the user if found, or empty
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks whether a user with the given username already exists.
     *
     * <p>Used during registration to reject duplicate usernames before trying to
     * insert and hitting the unique constraint at the database level.
     *
     * @param username the login name to check
     * @return {@code true} if at least one user with that username exists
     */
    boolean existsByUsername(String username);
}
