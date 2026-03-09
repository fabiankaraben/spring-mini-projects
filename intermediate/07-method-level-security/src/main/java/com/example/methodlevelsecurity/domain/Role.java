package com.example.methodlevelsecurity.domain;

/**
 * Enumeration of all roles available in the system.
 *
 * <p>Spring Security requires granted authority strings to start with the prefix
 * {@code ROLE_} when using the {@code hasRole('X')} SpEL helper (which internally
 * prepends the prefix). The enum names here already include the prefix so they can
 * be used directly as authority strings.</p>
 *
 * <ul>
 *   <li>{@link #ROLE_USER}      – regular authenticated user; can manage only their own documents.</li>
 *   <li>{@link #ROLE_MODERATOR} – can read and moderate content from all users.</li>
 *   <li>{@link #ROLE_ADMIN}     – full administrative access; can perform any operation.</li>
 * </ul>
 */
public enum Role {

    /** Standard user. Can create, read, update, and delete their own documents. */
    ROLE_USER,

    /** Moderator. Can read all documents and flag or archive them. */
    ROLE_MODERATOR,

    /** Administrator. Full access: can manage users and all documents. */
    ROLE_ADMIN
}
