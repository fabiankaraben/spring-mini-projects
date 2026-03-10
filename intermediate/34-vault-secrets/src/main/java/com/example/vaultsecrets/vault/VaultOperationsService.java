package com.example.vaultsecrets.vault;

import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;

/**
 * Low-level Vault operations service.
 *
 * <p>This class wraps Spring Vault's {@link VaultTemplate} to provide
 * typed read/write operations against the KV v2 secrets engine.
 * It is intentionally kept thin — business logic lives in
 * {@code CredentialService}.</p>
 *
 * <h2>Vault KV v2 path convention</h2>
 * <p>For a secret stored at logical path {@code "myapp/db"} under the
 * KV v2 mount {@code "secret"}, the actual API paths are:</p>
 * <ul>
 *   <li>Write: {@code PUT  /v1/secret/data/myapp/db}</li>
 *   <li>Read:  {@code GET  /v1/secret/data/myapp/db}</li>
 *   <li>Delete:{@code DELETE /v1/secret/data/myapp/db}</li>
 * </ul>
 *
 * <p>{@link VaultTemplate} handles prepending {@code /data/} when using the
 * KV v2 API via {@code VaultTemplate.opsForVersionedKeyValue(mount)}.</p>
 *
 * <h2>Secret envelope</h2>
 * <p>KV v2 wraps the secret in an envelope:
 * {@code { "data": { ... user data ... }, "metadata": { ... } }}.
 * {@link VaultResponse#getData()} returns the outer data map.
 * The user's key-value pairs are nested under the {@code "data"} key
 * inside that map.</p>
 */
@Service
public class VaultOperationsService {

    /**
     * The name of the KV v2 mount in Vault.
     * By default, Vault ships with a KV v2 mount at "secret".
     */
    private static final String KV_MOUNT = "secret";

    /**
     * VaultTemplate: the primary Spring Vault API for interacting with Vault.
     * Analogous to JdbcTemplate or RestTemplate in concept.
     * It handles authentication, serialization, and HTTP communication with Vault.
     */
    private final VaultTemplate vaultTemplate;

    public VaultOperationsService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    /**
     * Writes (or overwrites) a secret at the specified Vault KV v2 path.
     *
     * <p>KV v2 always creates a new version when writing; previous versions
     * are retained by Vault and can be retrieved or rolled back.</p>
     *
     * @param path       the logical KV path, e.g. {@code "myapp/db"}.
     *                   Do NOT include the mount name or {@code "/data/"} prefix.
     * @param secretData map of key-value pairs to store
     */
    public void writeSecret(String path, Map<String, Object> secretData) {
        // VaultTemplate.write() sends a PUT request to /v1/{mount}/data/{path}
        // for KV v2. The data is wrapped in the required envelope automatically.
        vaultTemplate.write(buildKvPath(path), Map.of("data", secretData));
    }

    /**
     * Reads a secret from the specified Vault KV v2 path.
     *
     * <p>Returns the latest version of the secret. If the path does not exist
     * or the token does not have read permission, {@code null} is returned.</p>
     *
     * @param path the logical KV path (e.g. {@code "myapp/db"})
     * @return a map containing all key-value pairs stored at that path,
     *         or {@code null} if the secret does not exist
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readSecret(String path) {
        // Read the raw KV v2 response; the envelope is: { "data": { "data": {...} } }
        VaultResponse response = vaultTemplate.read(buildKvPath(path));
        if (response == null || response.getData() == null) {
            return null;
        }
        // Unwrap the inner "data" map from the KV v2 envelope
        Object innerData = response.getData().get("data");
        if (innerData instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * Deletes the latest version of a secret at the specified Vault KV v2 path.
     *
     * <p>In KV v2, "delete" soft-deletes the latest version — the data is marked
     * as deleted but retained. Use "destroy" to permanently remove a version.</p>
     *
     * @param path the logical KV path (e.g. {@code "myapp/db"})
     */
    public void deleteSecret(String path) {
        // DELETE /v1/secret/data/{path} soft-deletes the latest version in KV v2
        vaultTemplate.delete(buildKvPath(path));
    }

    /**
     * Builds the full KV v2 API path from a logical user-supplied path.
     *
     * <p>KV v2 requires the "/data/" segment between the mount name and the
     * secret path.  For example:</p>
     * <ul>
     *   <li>Input:  {@code "myapp/db"}</li>
     *   <li>Output: {@code "secret/data/myapp/db"}</li>
     * </ul>
     *
     * @param logicalPath the user-supplied path (without mount or "/data/")
     * @return the full path usable with VaultTemplate
     */
    private String buildKvPath(String logicalPath) {
        // Strip any leading slash to avoid double-slash in the path
        String sanitized = logicalPath.startsWith("/") ? logicalPath.substring(1) : logicalPath;
        // Also strip the "secret/" prefix if the caller included it, to avoid duplication
        if (sanitized.startsWith(KV_MOUNT + "/")) {
            sanitized = sanitized.substring(KV_MOUNT.length() + 1);
        }
        // Also strip "data/" prefix if caller included it
        if (sanitized.startsWith("data/")) {
            sanitized = sanitized.substring("data/".length());
        }
        return KV_MOUNT + "/data/" + sanitized;
    }
}
