package com.example.jwtvalidation.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JPA entity representing an application user stored in the {@code users} table.
 *
 * <p>This class is the central domain object of the project. It holds the
 * credentials ({@code username} / {@code password}) and the single {@link Role}
 * that drives Spring Security's authorisation decisions.</p>
 *
 * <p>Passwords are <em>always</em> stored as a BCrypt hash – never in plain text.
 * Hashing is performed by {@code UserService} before persisting a new user.</p>
 *
 * <h2>Why "users" and not "user"?</h2>
 * <p>{@code user} is a reserved keyword in PostgreSQL. Using it as a table name
 * requires quoting everywhere. The table name {@code users} avoids this.</p>
 */
@Entity
@Table(name = "users") // "user" is a reserved keyword in PostgreSQL, so we use "users"
public class User {

    /**
     * Auto-generated surrogate primary key. Uses the database IDENTITY strategy
     * so PostgreSQL manages the sequence automatically.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login name chosen by the user. Must be 3–50 characters.
     * The unique constraint is enforced both at the database level and via
     * {@code UserService} to produce friendly error messages.
     */
    @Column(nullable = false, unique = true, length = 50)
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    /**
     * BCrypt-hashed password. The raw value is never stored here after
     * {@code UserService} encodes it on registration.
     */
    @Column(nullable = false)
    @NotBlank
    private String password;

    /**
     * The role of this user. Stored as a string (e.g. {@code "ROLE_USER"})
     * in the database. Used by Spring Security when building the
     * {@code Authentication} object's authorities list.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA; do not call directly. */
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

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    @Override
    public String toString() {
        // Intentionally omit password from toString to avoid accidental log exposure
        return "User{id=" + id + ", username='" + username + "', role=" + role + "}";
    }
}
