package com.example.validationgroups.dto;

import com.example.validationgroups.domain.User;

import java.time.LocalDateTime;

/**
 * Read-only response DTO returned by the API for user resources.
 *
 * <p>Using a dedicated response record keeps sensitive data (like the hashed
 * password) out of the HTTP response.  Only the fields that are safe and useful
 * for API consumers are included.</p>
 *
 * @param id        Auto-generated user identifier.
 * @param name      Display name.
 * @param email     Email address.
 * @param role      User role (e.g. "USER", "ADMIN").
 * @param active    Whether the account is currently active.
 * @param createdAt Timestamp of account creation.
 * @param updatedAt Timestamp of the most recent update.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Factory method that maps a {@link User} entity to a {@link UserResponse} DTO.
     *
     * <p>The password is deliberately excluded – never expose credentials in API responses.</p>
     *
     * @param user the entity to map
     * @return a new {@link UserResponse} populated from the entity fields
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
