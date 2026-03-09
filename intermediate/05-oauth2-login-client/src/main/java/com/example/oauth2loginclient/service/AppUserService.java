package com.example.oauth2loginclient.service;

import com.example.oauth2loginclient.domain.AppUser;
import com.example.oauth2loginclient.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for {@link AppUser} entities.
 *
 * <p>This layer encapsulates all business logic related to user management so
 * that the OAuth2 callback handler and REST controllers remain thin. It acts as
 * the single point of interaction with the JPA repository.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Upsert a user profile on every successful OAuth2 login</li>
 *   <li>Expose query helpers for controller and test use</li>
 * </ul>
 * </p>
 */
@Service
public class AppUserService {

    /** Repository that provides CRUD operations backed by PostgreSQL. */
    private final AppUserRepository userRepository;

    /**
     * Constructor injection is preferred over field injection because it makes
     * dependencies explicit and simplifies unit testing without a Spring context.
     */
    public AppUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ── Core upsert logic ────────────────────────────────────────────────────

    /**
     * Persists or refreshes a user record after a successful OAuth2 login.
     *
     * <p>The method implements an <em>upsert</em> strategy:
     * <ul>
     *   <li>If no row exists for the given {@code provider} / {@code providerId}
     *       pair a new {@link AppUser} is created and saved.</li>
     *   <li>If a row already exists its mutable attributes (name, email,
     *       avatarUrl) are updated to reflect any changes in the provider's
     *       profile, and {@code lastLoginAt} is refreshed to the current time.</li>
     * </ul>
     * </p>
     *
     * <p>The method is annotated with {@link Transactional} so that reads and
     * writes happen within the same database transaction; Hibernate will flush
     * any dirty state at commit time.</p>
     *
     * @param provider   OAuth2 provider identifier, e.g. "github" or "google"
     * @param providerId opaque user identifier returned by the provider
     * @param name       display name from the provider profile (may be null)
     * @param email      email address from the provider profile (may be null)
     * @param avatarUrl  URL of the user's avatar image (may be null)
     * @return the saved (new or updated) {@link AppUser} entity
     */
    @Transactional
    public AppUser upsertUser(String provider, String providerId,
                              String name, String email, String avatarUrl) {

        // Look for an existing user with the same provider + subject pair
        Optional<AppUser> existing = userRepository.findByProviderAndProviderId(provider, providerId);

        if (existing.isPresent()) {
            // UPDATE path – refresh mutable profile attributes
            AppUser user = existing.get();
            user.setName(name);
            user.setEmail(email);
            user.setAvatarUrl(avatarUrl);
            user.setLastLoginAt(Instant.now());
            // JPA will detect the dirty state and issue an UPDATE at flush time
            return userRepository.save(user);
        } else {
            // INSERT path – create a brand-new user record
            AppUser newUser = new AppUser(provider, providerId, name, email, avatarUrl);
            return userRepository.save(newUser);
        }
    }

    // ── Query helpers ────────────────────────────────────────────────────────

    /**
     * Returns all stored users ordered by their database primary key.
     *
     * <p>This is used by the admin endpoint to list all users that have ever
     * logged in. In a real application this would be paginated.</p>
     *
     * @return an unmodifiable list of all {@link AppUser} entities
     */
    @Transactional(readOnly = true)
    public List<AppUser> findAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Find a single user by their database primary key.
     *
     * @param id the surrogate primary key
     * @return an {@link Optional} containing the user, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Find a user by the provider + provider-subject identifier combination.
     *
     * @param provider   OAuth2 provider identifier
     * @param providerId provider's opaque user identifier
     * @return an {@link Optional} containing the user, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> findByProviderAndProviderId(String provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId);
    }
}
