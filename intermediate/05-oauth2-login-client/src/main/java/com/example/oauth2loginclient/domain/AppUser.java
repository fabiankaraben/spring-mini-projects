package com.example.oauth2loginclient.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity that stores a locally-persisted profile for every user that
 * authenticates via an OAuth2 provider (GitHub or Google).
 *
 * <p>Each row uniquely identifies a user by the combination of
 * {@code provider} (e.g. "github") and {@code providerId} (the opaque subject
 * identifier returned by the provider). This allows the same email address to
 * exist across multiple providers without conflating the accounts.</p>
 *
 * <p>The entity is intentionally minimal; it stores only the attributes that
 * are common to both GitHub and Google responses.</p>
 */
@Entity
@Table(
    name = "app_users",
    // Composite unique constraint: one row per provider+subject combination
    uniqueConstraints = @UniqueConstraint(
        name = "uq_app_users_provider_provider_id",
        columnNames = {"provider", "provider_id"}
    )
)
public class AppUser {

    /** Auto-generated surrogate primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short string identifying the OAuth2 provider, e.g. "github" or "google".
     * Derived from the Spring Security {@code registrationId}.
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * Opaque unique identifier assigned by the provider (GitHub: numeric user
     * id as string; Google: "sub" claim of the ID token).
     */
    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    /** Display name returned by the provider (may be null for some accounts). */
    @Column(length = 255)
    private String name;

    /** Primary email address returned by the provider (may be null). */
    @Column(length = 255)
    private String email;

    /** URL of the user's avatar image returned by the provider (may be null). */
    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    /**
     * Timestamp of the first login (set once on creation).
     * Using {@code Instant} maps to a TIMESTAMP WITH TIME ZONE column in
     * PostgreSQL, which is the recommended type for audit timestamps.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the most recent login (updated on every successful OAuth2
     * authentication for this user).
     */
    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    // ── JPA lifecycle callbacks ──────────────────────────────────────────────

    /**
     * Automatically set {@code createdAt} and {@code lastLoginAt} when a new
     * entity is first persisted.
     */
    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastLoginAt = now;
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    /** No-arg constructor required by JPA. */
    protected AppUser() {}

    /**
     * Full constructor used by {@code CustomOAuth2UserService} when creating a
     * new user record after a first-time OAuth2 login.
     */
    public AppUser(String provider, String providerId, String name,
                   String email, String avatarUrl) {
        this.provider   = provider;
        this.providerId = providerId;
        this.name       = name;
        this.email      = email;
        this.avatarUrl  = avatarUrl;
    }

    // ── Getters and setters ──────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getProvider() { return provider; }

    public String getProviderId() { return providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
