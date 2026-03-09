package com.example.methodlevelsecurity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JPA entity representing an application user stored in the {@code users} table.
 *
 * <p>Each user has a unique username, a BCrypt-hashed password, and a single
 * {@link Role} that drives all authorisation decisions evaluated by
 * {@code @PreAuthorize}, {@code @PostAuthorize}, and {@code @Secured} annotations.</p>
 *
 * <p>Passwords are <em>always</em> stored as a BCrypt hash – never as plain text.
 * Encoding is performed by {@link com.example.methodlevelsecurity.service.UserService}
 * before any persistence operation.</p>
 *
 * <h2>Table name "users"</h2>
 * <p>{@code user} is a reserved keyword in PostgreSQL, so we use {@code users}
 * to avoid the need for quoting identifiers everywhere.</p>
 */
@Entity
@Table(name = "users") // "user" is a reserved keyword in PostgreSQL
public class User {

    /**
     * Auto-generated surrogate primary key.
     * Uses {@code IDENTITY} so PostgreSQL manages the sequence automatically.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login name. Must be between 3 and 50 characters.
     * Uniqueness is enforced both by the database unique constraint and
     * by a pre-check in {@link com.example.methodlevelsecurity.service.UserService}.
     */
    @Column(nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    /**
     * BCrypt-encoded password. The raw (plain-text) password is never stored here.
     */
    @Column(nullable = false)
    @NotBlank
    private String password;

    /**
     * The single role assigned to this user.
     * Stored as the enum constant name (e.g. {@code "ROLE_ADMIN"}) via
     * {@code EnumType.STRING} so the DB column is human-readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA; do not use in application code. */
    protected User() {}

    /**
     * Convenience constructor used by service and test code.
     *
     * @param username login name (3–50 chars)
     * @param password BCrypt-encoded password
     * @param role     authority granted to this user
     */
    public User(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }

    public void setRole(Role role) { this.role = role; }

    @Override
    public String toString() {
        // Intentionally omit password from toString to avoid accidental log exposure
        return "User{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}
