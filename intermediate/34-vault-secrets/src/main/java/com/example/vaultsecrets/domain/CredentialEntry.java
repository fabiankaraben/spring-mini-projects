package com.example.vaultsecrets.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/**
 * JPA entity representing metadata about a secret stored in Vault.
 *
 * <h2>What is stored here vs in Vault</h2>
 * <ul>
 *   <li><strong>This table (H2)</strong>: the Vault path, a human-readable name,
 *       and a description. No sensitive values.</li>
 *   <li><strong>Vault (KV v2)</strong>: the actual key-value secret pairs
 *       (e.g. {@code username}, {@code password}, {@code apiKey}).</li>
 * </ul>
 *
 * <p>This separation mirrors real-world secret management: a database stores
 * "where" secrets live, while Vault stores "what" the secrets are.</p>
 */
@Entity
@Table(name = "credential_entries")
public class CredentialEntry {

    /**
     * Auto-generated primary key.
     * Uses the database IDENTITY strategy (suitable for H2 and most RDBMS).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable name for this credential (e.g. "prod-db-credentials").
     * Must be unique so callers can look up a specific credential by name.
     */
    @Column(nullable = false, unique = true)
    @NotBlank
    private String name;

    /**
     * The Vault KV path where the secret is stored.
     *
     * <p>For the KV v2 engine mounted at "secret", a path of
     * {@code "secret/data/myapp/db"} stores values accessible at
     * {@code GET /v1/secret/data/myapp/db}.</p>
     */
    @Column(nullable = false)
    @NotBlank
    private String vaultPath;

    /**
     * Optional human-readable description of what this credential is for.
     * E.g. "Production database credentials for the reporting service."
     */
    @Column(length = 512)
    private String description;

    /**
     * Timestamp of when this metadata record was created.
     * Set server-side on first persist; never updated.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    protected CredentialEntry() {}

    /**
     * Full constructor for creating a new credential entry.
     *
     * @param name        human-readable name (must be unique)
     * @param vaultPath   path in Vault KV v2 where the secret is stored
     * @param description optional description of this credential's purpose
     */
    public CredentialEntry(String name, String vaultPath, String description) {
        this.name = name;
        this.vaultPath = vaultPath;
        this.description = description;
        this.createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Lifecycle callback: set createdAt before first insert
    // -------------------------------------------------------------------------

    /**
     * JPA lifecycle callback: sets {@link #createdAt} just before the entity
     * is persisted for the first time.  This guarantees the timestamp is always
     * set even if the constructor isn't used (e.g. via reflection).
     */
    @PrePersist
    void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // -------------------------------------------------------------------------
    // Getters & Setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVaultPath() {
        return vaultPath;
    }

    public void setVaultPath(String vaultPath) {
        this.vaultPath = vaultPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
