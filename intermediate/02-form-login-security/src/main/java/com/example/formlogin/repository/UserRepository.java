package com.example.formlogin.repository;

import com.example.formlogin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring Data automatically generates the implementation at runtime, so no
 * manual SQL or boilerplate DAO code is needed. The single custom method
 * {@link #findByUsername(String)} is used by the security layer to look up users
 * during the authentication process.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Looks up a user by their unique login name.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user if found, or empty if not
     */
    Optional<User> findByUsername(String username);
}
