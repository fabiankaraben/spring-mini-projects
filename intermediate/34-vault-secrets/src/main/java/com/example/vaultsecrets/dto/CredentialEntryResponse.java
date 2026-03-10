package com.example.vaultsecrets.dto;

import com.example.vaultsecrets.domain.CredentialEntry;

import java.time.Instant;

/**
 * Response DTO for a {@link CredentialEntry} metadata record.
 *
 * <p>This DTO contains only metadata — never any secret values.
 * Secret values are retrieved from Vault on demand via a separate endpoint.</p>
 *
 * <p>Using a dedicated DTO (instead of exposing the JPA entity directly)
 * is a best practice that:</p>
 * <ul>
 *   <li>Decouples the API contract from the persistence model</li>
 *   <li>Prevents accidental exposure of JPA internals (lazy fields, proxies)</li>
 *   <li>Allows the API shape to evolve independently of the database schema</li>
 * </ul>
 */
public class CredentialEntryResponse {

    /** Database-generated primary key. */
    private Long id;

    /** Unique human-readable name for this credential entry. */
    private String name;

    /**
     * Vault KV v2 path where the secret is stored.
     * Callers can use this path to understand where to find the secret in Vault.
     */
    private String vaultPath;

    /** Optional free-text description of this credential's purpose. */
    private String description;

    /** Timestamp when this metadata record was first created. */
    private Instant createdAt;

    /**
     * Static factory method to convert a JPA entity to this DTO.
     *
     * <p>Using a static factory instead of a constructor makes call sites
     * more readable: {@code CredentialEntryResponse.from(entry)}.</p>
     *
     * @param entry the JPA entity to convert
     * @return a new response DTO populated from the entity
     */
    public static CredentialEntryResponse from(CredentialEntry entry) {
        CredentialEntryResponse dto = new CredentialEntryResponse();
        dto.id = entry.getId();
        dto.name = entry.getName();
        dto.vaultPath = entry.getVaultPath();
        dto.description = entry.getDescription();
        dto.createdAt = entry.getCreatedAt();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVaultPath() {
        return vaultPath;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
