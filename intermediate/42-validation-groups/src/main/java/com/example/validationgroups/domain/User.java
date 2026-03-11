package com.example.validationgroups.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * JPA entity representing a user stored in the {@code users} table.
 *
 * <p>This entity is intentionally kept simple so the focus stays on the
 * <em>validation layer</em> rather than complex domain logic.  The real
 * demonstration of validation groups lives in {@code UserRequest} and the
 * controller methods that select which group to activate.</p>
 *
 * <h2>Fields and their validation story</h2>
 * <ul>
 *   <li><strong>name</strong> – validated on CREATE and UPDATE (always editable).</li>
 *   <li><strong>email</strong> – validated on CREATE and UPDATE (always editable).</li>
 *   <li><strong>password</strong> – stored as a plain string for simplicity
 *       (in production, use BCrypt).  Required on CREATE; ignored on UPDATE;
 *       changed through the dedicated change-password endpoint.</li>
 *   <li><strong>role</strong> – set on CREATE; cannot be changed via the normal
 *       update endpoint (no {@code OnUpdate} constraint on the DTO field).</li>
 * </ul>
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * Auto-generated surrogate primary key managed by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Display name of the user.  Must be 2–100 characters and non-blank.
     * Validated on both CREATE and UPDATE operations.
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Unique email address used for identification.
     * Validated on both CREATE and UPDATE operations.
     * The database enforces uniqueness at the schema level.
     */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * Hashed password.  In this demo we store it as plain text for clarity.
     * Required on CREATE; can only be changed through the change-password endpoint.
     */
    @Column(nullable = false)
    private String password;

    /**
     * Role of the user (e.g. "USER", "ADMIN", "MODERATOR").
     * Set on CREATE; not editable through the standard update endpoint.
     */
    @Column(nullable = false, length = 50)
    private String role;

    /**
     * Whether the user account is active.  Defaults to {@code true} on creation.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Timestamp of when the user was first persisted (creation audit).
     * Set once on INSERT via {@link #prePersist()} and never changed.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp of the most recent update to this user record.
     * Updated on every MERGE/UPDATE via {@link #preUpdate()}.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── JPA lifecycle callbacks ────────────────────────────────────────────────

    /**
     * Populates audit timestamps before the first INSERT.
     */
    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Updates the {@code updatedAt} timestamp before every subsequent UPDATE.
     */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required no-arg constructor for JPA. Do not use from application code. */
    protected User() {}

    /**
     * Convenience constructor for creating a new user.
     *
     * @param name     display name
     * @param email    unique email address
     * @param password plain-text password (hash in production)
     * @param role     role identifier (e.g. "USER")
     */
    public User(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = true;
    }

    // ── Getters and Setters ───────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id
                + ", name='" + name + "'"
                + ", email='" + email + "'"
                + ", role='" + role + "'"
                + ", active=" + active + "}";
    }
}
