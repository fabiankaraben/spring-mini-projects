package com.example.formlogin.entity;

import jakarta.persistence.*;

/**
 * JPA entity representing an application user stored in the database.
 *
 * <p>Each user has a unique username, a BCrypt-encoded password, and a single
 * role string (e.g. "USER" or "ADMIN"). Spring Security will prepend "ROLE_"
 * automatically when the role is loaded via {@code .roles()} in
 * {@link com.example.formlogin.security.UserDetailsServiceImpl}.
 */
@Entity
@Table(name = "users")
public class User {

    /** Auto-generated primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique login name – used as the principal name in the security context. */
    @Column(unique = true, nullable = false)
    private String username;

    /**
     * BCrypt-hashed password.
     * The plain-text password is never stored; only the hash is persisted.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Single role for this user (e.g. "USER" or "ADMIN").
     * Spring Security expects the value WITHOUT the "ROLE_" prefix when using
     * {@code .roles()}, but WITH the prefix when using {@code .authorities()}.
     */
    @Column(nullable = false)
    private String role;

    /** Required by JPA. */
    public User() {
    }

    /**
     * Convenience constructor used by the data initializer.
     *
     * @param username the unique login name
     * @param password the BCrypt-encoded password hash
     * @param role     the role name without "ROLE_" prefix (e.g. "USER", "ADMIN")
     */
    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
