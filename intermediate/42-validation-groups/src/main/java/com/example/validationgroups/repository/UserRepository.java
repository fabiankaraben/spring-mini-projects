package com.example.validationgroups.repository;

import com.example.validationgroups.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring automatically generates the implementation at runtime based on the
 * method name conventions.  No SQL or JPQL needs to be written for simple queries.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their email address.
     *
     * <p>Used to check for email uniqueness before creating or updating a user.</p>
     *
     * @param email the email address to search for (case-sensitive)
     * @return an {@link Optional} containing the user if found, or empty
     */
    Optional<User> findByEmail(String email);

    /**
     * Returns all users whose name contains the given substring, ignoring case.
     *
     * <p>For example, searching "john" would match "John Doe" and "JOHN SMITH".</p>
     *
     * @param name the substring to search for within user names
     * @return a list of matching users, possibly empty
     */
    List<User> findByNameContainingIgnoreCase(String name);

    /**
     * Returns all users with the specified role.
     *
     * @param role the role to filter by (e.g. "ADMIN", "USER")
     * @return a list of users with that role, possibly empty
     */
    List<User> findByRole(String role);

    /**
     * Checks whether a user with the given email address already exists.
     *
     * <p>Used for fast duplicate-email validation without loading the full entity.</p>
     *
     * @param email the email address to check
     * @return {@code true} if at least one user has this email, {@code false} otherwise
     */
    boolean existsByEmail(String email);
}
