package com.example.vaultsecrets.service;

import com.example.vaultsecrets.domain.CredentialEntry;
import com.example.vaultsecrets.dto.StoreSecretRequest;
import com.example.vaultsecrets.repository.CredentialEntryRepository;
import com.example.vaultsecrets.vault.VaultOperationsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic layer for credential management.
 *
 * <p>This service orchestrates two separate stores:</p>
 * <ol>
 *   <li><strong>Vault</strong> (via {@link VaultOperationsService}): stores the actual
 *       secret key-value data (passwords, API keys, tokens).</li>
 *   <li><strong>H2 database</strong> (via {@link CredentialEntryRepository}): stores
 *       metadata about each credential — name, Vault path, and description —
 *       so that the application can enumerate and look up credentials by name
 *       without contacting Vault for the index.</li>
 * </ol>
 *
 * <h2>Why keep metadata in the database?</h2>
 * <p>Vault's KV engine does not natively support "list all secrets across all paths".
 * Keeping a metadata table lets us enumerate credentials, search by name, and
 * display descriptions without an extra Vault LIST call per entry.</p>
 *
 * <h2>Security invariant</h2>
 * <p>Secret values NEVER flow through the relational database.
 * The database only holds the Vault path; secret retrieval always goes
 * directly to Vault at query time.</p>
 */
@Service
public class CredentialService {

    /** Low-level Vault read/write operations. */
    private final VaultOperationsService vaultOperationsService;

    /** JPA repository for credential metadata. */
    private final CredentialEntryRepository credentialEntryRepository;

    public CredentialService(VaultOperationsService vaultOperationsService,
                             CredentialEntryRepository credentialEntryRepository) {
        this.vaultOperationsService = vaultOperationsService;
        this.credentialEntryRepository = credentialEntryRepository;
    }

    /**
     * Stores a new secret in Vault and registers its metadata in the database.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Validate that the name is not already taken.</li>
     *   <li>Write the secret key-value data to Vault.</li>
     *   <li>Persist the metadata (name, path, description) to H2.</li>
     * </ol>
     *
     * <p>The database transaction is started before the Vault write.
     * If the metadata save fails, the transaction rolls back (but the Vault
     * write already happened — this is an acceptable trade-off for a
     * demonstration project; a production system would use a saga or
     * compensating transaction).</p>
     *
     * @param request DTO with name, vaultPath, description, and secretData
     * @return the saved {@link CredentialEntry} metadata entity
     * @throws IllegalArgumentException if a credential with the same name already exists
     */
    @Transactional
    public CredentialEntry storeSecret(StoreSecretRequest request) {
        // Guard: prevent duplicate names in the metadata table
        if (credentialEntryRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException(
                    "A credential with name '" + request.getName() + "' already exists.");
        }

        // Step 1: write the actual secret values to Vault
        // The secret data map is typed as Map<String, Object> because Vault's
        // generic write API accepts any value type, even though callers send strings.
        Map<String, Object> vaultData = new HashMap<>(request.getSecretData());
        vaultOperationsService.writeSecret(request.getVaultPath(), vaultData);

        // Step 2: save metadata (path + description) to the relational database.
        // The secret values themselves are NOT persisted here.
        CredentialEntry entry = new CredentialEntry(
                request.getName(),
                request.getVaultPath(),
                request.getDescription()
        );
        return credentialEntryRepository.save(entry);
    }

    /**
     * Returns all credential metadata records from the database.
     *
     * <p>This does NOT retrieve secret values from Vault; it only returns
     * the name, path, and description of each registered credential.</p>
     *
     * @return list of all {@link CredentialEntry} records
     */
    @Transactional(readOnly = true)
    public List<CredentialEntry> listCredentials() {
        return credentialEntryRepository.findAll();
    }

    /**
     * Retrieves the actual secret data from Vault for a credential identified by name.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Look up the metadata record to find the Vault path.</li>
     *   <li>Call Vault to retrieve the secret at that path.</li>
     * </ol>
     *
     * @param name the unique name of the credential entry
     * @return a map of secret key-value pairs retrieved from Vault
     * @throws IllegalArgumentException if no credential with the given name is registered
     * @throws IllegalStateException    if the secret does not exist in Vault
     *                                  (metadata exists but Vault data is missing)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSecretByName(String name) {
        // Look up the Vault path from the metadata table
        CredentialEntry entry = credentialEntryRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No credential registered with name: " + name));

        // Read the actual secret values from Vault
        Map<String, Object> secretData = vaultOperationsService.readSecret(entry.getVaultPath());
        if (secretData == null) {
            throw new IllegalStateException(
                    "Secret exists in metadata but was not found in Vault at path: "
                    + entry.getVaultPath());
        }
        return secretData;
    }

    /**
     * Retrieves credential metadata by name (without fetching the secret from Vault).
     *
     * @param name the unique credential name
     * @return an Optional containing the entry if found
     */
    @Transactional(readOnly = true)
    public Optional<CredentialEntry> getMetadataByName(String name) {
        return credentialEntryRepository.findByName(name);
    }

    /**
     * Deletes a credential: removes it from Vault and from the metadata table.
     *
     * <p>The Vault delete is a soft-delete (marks the latest version as deleted).
     * The metadata row is hard-deleted from the relational database.</p>
     *
     * @param name the unique name of the credential to delete
     * @throws IllegalArgumentException if no credential with the given name is registered
     */
    @Transactional
    public void deleteCredential(String name) {
        CredentialEntry entry = credentialEntryRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No credential registered with name: " + name));

        // Soft-delete the secret from Vault
        vaultOperationsService.deleteSecret(entry.getVaultPath());

        // Hard-delete the metadata from the relational database
        credentialEntryRepository.delete(entry);
    }
}
