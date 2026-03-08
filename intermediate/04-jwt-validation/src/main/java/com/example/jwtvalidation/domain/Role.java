package com.example.jwtvalidation.domain;

/**
 * Enumeration of the roles that a user can hold within this application.
 *
 * <p>Each constant maps directly to a Spring Security {@code GrantedAuthority}
 * string (e.g. {@code "ROLE_USER"}, {@code "ROLE_ADMIN"}). The prefix
 * {@code ROLE_} is required by Spring Security's default authorisation
 * expressions such as {@code hasRole("USER")} and {@code hasRole("ADMIN")}.</p>
 *
 * <h2>Why an enum?</h2>
 * <p>Using an enum instead of a free-form string prevents typos and makes it
 * easy to see all valid roles at a glance. The {@code @Enumerated(EnumType.STRING)}
 * annotation on the {@link User} entity ensures that the database stores the
 * literal name (e.g. {@code "ROLE_USER"}) rather than the ordinal (0, 1, …),
 * which would break if the order ever changed.</p>
 */
public enum Role {

    /**
     * Standard authenticated user.
     * Can access endpoints protected by {@code hasRole("USER")} or
     * {@code hasAnyRole("USER", "ADMIN")}.
     */
    ROLE_USER,

    /**
     * Privileged administrator.
     * Can access endpoints protected by {@code hasRole("ADMIN")}.
     */
    ROLE_ADMIN
}
