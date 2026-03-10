package com.example.vaultsecrets.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Request DTO for storing a new secret in Vault.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #name} — a unique, human-readable identifier for the credential
 *       (e.g. {@code "prod-db-credentials"}).  Stored in the metadata table.</li>
 *   <li>{@link #vaultPath} — the Vault KV v2 path where the secret will be written
 *       (e.g. {@code "secret/myapp/db"}).  The {@code /data/} segment is added
 *       automatically by the Vault client library.</li>
 *   <li>{@link #description} — optional free-text description.</li>
 *   <li>{@link #secretData} — the actual key-value pairs to store in Vault
 *       (e.g. {@code {"username": "admin", "password": "s3cr3t"}}).
 *       These values are sent to Vault and never persisted in the application
 *       database.</li>
 * </ul>
 */
public class StoreSecretRequest {

    /**
     * Unique human-readable name for this credential entry.
     * Used to look up the entry by name in the metadata table.
     */
    @NotBlank(message = "name must not be blank")
    private String name;

    /**
     * Vault KV v2 path where the secret will be stored.
     * Example: {@code "secret/myapp/db"} or {@code "secret/services/payment-api"}.
     */
    @NotBlank(message = "vaultPath must not be blank")
    private String vaultPath;

    /** Optional human-readable description of this credential's purpose. */
    private String description;

    /**
     * The actual secret key-value pairs to write to Vault.
     * Must contain at least one entry.
     * Example: {@code {"username": "admin", "password": "s3cr3t"}}.
     */
    @NotEmpty(message = "secretData must not be empty")
    private Map<String, String> secretData;

    /** Default constructor (required for JSON deserialization). */
    public StoreSecretRequest() {}

    /**
     * Full constructor.
     *
     * @param name        unique name for the credential entry
     * @param vaultPath   Vault KV v2 path
     * @param description optional description
     * @param secretData  the actual key-value secret pairs
     */
    public StoreSecretRequest(String name, String vaultPath, String description,
                               Map<String, String> secretData) {
        this.name = name;
        this.vaultPath = vaultPath;
        this.description = description;
        this.secretData = secretData;
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

    public Map<String, String> getSecretData() {
        return secretData;
    }

    public void setSecretData(Map<String, String> secretData) {
        this.secretData = secretData;
    }
}
