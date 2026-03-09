package com.example.oauth2loginclient.dto;

import com.example.oauth2loginclient.domain.AppUser;

import java.time.Instant;

/**
 * Read-only Data Transfer Object that exposes a sanitized view of an
 * {@link AppUser} entity over the REST API.
 *
 * <p>Using a separate DTO prevents JPA managed-entity state from leaking into
 * the HTTP response and gives us full control over which fields are serialized.
 * The record syntax (Java 16+) provides an immutable, concise value type.</p>
 *
 * @param id          surrogate database primary key
 * @param provider    OAuth2 provider name, e.g. "github" or "google"
 * @param name        display name as returned by the provider
 * @param email       primary email address returned by the provider (may be null)
 * @param avatarUrl   URL of the user's avatar image (may be null)
 * @param createdAt   timestamp of the first successful login
 * @param lastLoginAt timestamp of the most recent successful login
 */
public record UserProfileDto(
        Long id,
        String provider,
        String name,
        String email,
        String avatarUrl,
        Instant createdAt,
        Instant lastLoginAt
) {

    /**
     * Factory method that converts an {@link AppUser} entity into a
     * {@link UserProfileDto}, avoiding constructor boilerplate at call sites.
     *
     * @param user the entity to convert; must not be null
     * @return a new DTO with values copied from the entity
     */
    public static UserProfileDto from(AppUser user) {
        return new UserProfileDto(
                user.getId(),
                user.getProvider(),
                user.getName(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
