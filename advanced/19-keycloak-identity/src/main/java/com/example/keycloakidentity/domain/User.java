package com.example.keycloakidentity.domain;

import java.time.Instant;

/**
 * Domain model representing a user managed by this backend service.
 *
 * <p><b>Important distinction:</b> This {@code User} object is <em>not</em> the Keycloak user.
 * Keycloak manages authentication and the user's identity (username, password, email verification).
 * This domain object represents additional user profile data that the application itself manages —
 * for example, application-specific preferences, roles, or metadata.
 *
 * <p>In a real application, this data would live in the application's own database (e.g., PostgreSQL),
 * linked to the Keycloak user by the {@code keycloakId} field, which corresponds to the {@code sub}
 * claim in the JWT issued by Keycloak.
 *
 * <p>For this educational project, users are stored in an in-memory list in the repository
 * to keep the focus on Keycloak integration rather than database concerns.
 *
 * <h2>Relationship to Keycloak</h2>
 * <pre>
 *   Keycloak User (sub = "uuid-from-keycloak")
 *       │
 *       └─── links to ───▶ User.keycloakId = "uuid-from-keycloak"
 * </pre>
 *
 * <p>When a Keycloak-authenticated user calls the API, their JWT's {@code sub} claim
 * (the Keycloak user UUID) can be matched to the {@code keycloakId} field here to
 * retrieve or update their application-level profile.
 */
public class User {

    /**
     * Application-internal unique identifier (auto-assigned by the repository).
     * This is separate from the Keycloak user UUID.
     */
    private Long id;

    /**
     * The Keycloak user UUID — the {@code sub} claim from the JWT.
     * Links this application user record to the corresponding Keycloak identity.
     * Nullable because demo users pre-seeded in the repo may not have a Keycloak counterpart.
     */
    private String keycloakId;

    /**
     * The user's display name (e.g., "John Doe").
     * Typically populated from Keycloak's {@code name} claim.
     */
    private String displayName;

    /**
     * The user's email address.
     * Corresponds to Keycloak's {@code email} claim in the JWT.
     */
    private String email;

    /**
     * The user's role within this application (e.g., "USER" or "ADMIN").
     * Note: This is the <em>application-level</em> role stored in our backend.
     * Keycloak roles (from {@code realm_access.roles}) are what Spring Security uses
     * for authorization. These two role systems may or may not be in sync.
     */
    private String role;

    /**
     * Whether this user account is currently active.
     * Inactive accounts can still exist in our database but cannot use the API.
     */
    private boolean active;

    /**
     * Timestamp when this user record was created in the application database.
     */
    private Instant createdAt;

    /**
     * Timestamp when this user record was last modified.
     */
    private Instant updatedAt;

    /**
     * Default no-args constructor required for frameworks and testing.
     */
    public User() {
    }

    /**
     * Full constructor for creating a complete User object (e.g., from a repository).
     *
     * @param id          the application-internal ID
     * @param keycloakId  the Keycloak user UUID (sub claim), may be null
     * @param displayName the user's display name
     * @param email       the user's email address
     * @param role        the application-level role ("USER" or "ADMIN")
     * @param active      whether the account is active
     * @param createdAt   creation timestamp
     * @param updatedAt   last-modified timestamp
     */
    public User(Long id, String keycloakId, String displayName, String email,
                String role, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.keycloakId = keycloakId;
        this.displayName = displayName;
        this.email = email;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", keycloakId='" + keycloakId + "', email='" + email
                + "', role='" + role + "', active=" + active + "}";
    }
}
