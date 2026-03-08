package com.example.jwtgeneration.domain;

/**
 * Represents the roles that can be assigned to a {@link User}.
 *
 * <p>Roles are stored as strings in the database (via
 * {@code @Enumerated(EnumType.STRING)}) and are prefixed with {@code ROLE_}
 * when exposed to Spring Security, which is the standard convention for
 * {@code GrantedAuthority} names used with {@code hasRole()} checks.
 */
public enum Role {

    /**
     * Standard user with read-only access to protected resources.
     */
    ROLE_USER,

    /**
     * Administrator with full access to all resources.
     */
    ROLE_ADMIN
}
