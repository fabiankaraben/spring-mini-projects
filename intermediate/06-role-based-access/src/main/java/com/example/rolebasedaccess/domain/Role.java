package com.example.rolebasedaccess.domain;

/**
 * Enumeration of the application roles used for access control.
 *
 * <p>Spring Security's role-based checks (e.g. {@code hasRole("ADMIN")},
 * {@code @PreAuthorize("hasRole('ADMIN')")}) strip the {@code "ROLE_"} prefix
 * automatically when comparing against a {@link org.springframework.security.core.GrantedAuthority}
 * whose name already starts with {@code "ROLE_"}. So:</p>
 * <ul>
 *   <li>{@code hasRole("ADMIN")} matches authority {@code "ROLE_ADMIN"}</li>
 *   <li>{@code hasAuthority("ROLE_ADMIN")} also matches (exact match, no stripping)</li>
 * </ul>
 *
 * <p>The role hierarchy implemented in this project is:</p>
 * <pre>
 *   ROLE_ADMIN ⊇ ROLE_MODERATOR ⊇ ROLE_USER
 * </pre>
 * <p>This means an ADMIN can access everything a MODERATOR or USER can, and a
 * MODERATOR can access everything a USER can.</p>
 */
public enum Role {

    /**
     * Standard authenticated user.
     * Can read public resources and their own profile.
     */
    ROLE_USER,

    /**
     * Content moderator.
     * Can manage (edit, hide) posts in addition to all ROLE_USER permissions.
     */
    ROLE_MODERATOR,

    /**
     * System administrator.
     * Has full access including user management and administrative operations.
     */
    ROLE_ADMIN
}
