package com.example.oauth2loginclient.repository;

import com.example.oauth2loginclient.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link AppUser} entities.
 *
 * <p>Spring Data automatically generates a proxy implementation at startup time,
 * providing standard CRUD operations via {@link JpaRepository} and the custom
 * finder method declared below.</p>
 */
@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Find a user by the combination of OAuth2 provider and the provider's own
     * subject identifier.
     *
     * <p>This is the primary lookup key used after the OAuth2 callback: the
     * {@code registrationId} (e.g. "github") is used as {@code provider} and
     * the provider's unique user id becomes {@code providerId}.</p>
     *
     * @param provider   short name of the OAuth2 provider, e.g. "github" or "google"
     * @param providerId opaque user identifier returned by the provider
     * @return an {@link Optional} containing the matching user, or empty if not found
     */
    Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
}
