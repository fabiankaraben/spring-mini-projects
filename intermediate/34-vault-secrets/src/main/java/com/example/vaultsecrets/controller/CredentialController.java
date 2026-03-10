package com.example.vaultsecrets.controller;

import com.example.vaultsecrets.domain.CredentialEntry;
import com.example.vaultsecrets.dto.CredentialEntryResponse;
import com.example.vaultsecrets.dto.StoreSecretRequest;
import com.example.vaultsecrets.service.CredentialService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the Vault Secrets API.
 *
 * <h2>Endpoint overview</h2>
 * <pre>
 * POST   /api/credentials          — Store a new secret in Vault + register metadata
 * GET    /api/credentials          — List all registered credential metadata (no secrets)
 * GET    /api/credentials/{name}   — Get metadata for a specific credential
 * GET    /api/credentials/{name}/secret — Retrieve the actual secret from Vault
 * DELETE /api/credentials/{name}   — Delete a credential from Vault + metadata table
 * </pre>
 *
 * <h2>Security note</h2>
 * <p>In a real production API, the endpoint that returns secret values
 * ({@code GET /api/credentials/{name}/secret}) should be protected by
 * authentication and authorization (e.g., OAuth 2.0 scopes, Spring Security).
 * This demo project omits auth to keep the focus on Vault integration.</p>
 */
@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    /** Business logic layer — delegates to Vault and the metadata repository. */
    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    // =========================================================================
    // POST /api/credentials — Store a new secret
    // =========================================================================

    /**
     * Stores a new secret in Vault and registers its metadata.
     *
     * <p>The request body must include:</p>
     * <ul>
     *   <li>{@code name} — unique identifier for this credential</li>
     *   <li>{@code vaultPath} — where to store it in Vault KV v2</li>
     *   <li>{@code secretData} — the actual key-value secret pairs</li>
     *   <li>{@code description} — optional free text</li>
     * </ul>
     *
     * @param request validated request DTO
     * @return 201 Created with the metadata response, or 409 Conflict if duplicate
     */
    @PostMapping
    public ResponseEntity<?> storeSecret(@Valid @RequestBody StoreSecretRequest request) {
        try {
            CredentialEntry entry = credentialService.storeSecret(request);
            // Return 201 Created with the metadata (no secret values in the response)
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(CredentialEntryResponse.from(entry));
        } catch (IllegalArgumentException ex) {
            // Duplicate name — return 409 Conflict with an error message
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/credentials — List all credential metadata
    // =========================================================================

    /**
     * Returns a list of all registered credential metadata records.
     *
     * <p>This endpoint does NOT return any secret values — only names,
     * Vault paths, descriptions, and timestamps.</p>
     *
     * @return 200 OK with a list of {@link CredentialEntryResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<CredentialEntryResponse>> listCredentials() {
        List<CredentialEntryResponse> responses = credentialService.listCredentials()
                .stream()
                .map(CredentialEntryResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // =========================================================================
    // GET /api/credentials/{name} — Get metadata for one credential
    // =========================================================================

    /**
     * Returns metadata for a single credential identified by name.
     *
     * <p>Does NOT return secret values. Use the {@code /secret} sub-resource
     * to retrieve the actual Vault data.</p>
     *
     * @param name the unique credential name
     * @return 200 OK with the metadata, or 404 Not Found
     */
    @GetMapping("/{name}")
    public ResponseEntity<?> getMetadata(@PathVariable String name) {
        return credentialService.getMetadataByName(name)
                .map(entry -> ResponseEntity.ok(CredentialEntryResponse.from(entry)))
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================================
    // GET /api/credentials/{name}/secret — Retrieve actual secret from Vault
    // =========================================================================

    /**
     * Retrieves the actual secret key-value data from Vault for the named credential.
     *
     * <p>This is the only endpoint that communicates with Vault at query time.
     * All other endpoints only interact with the metadata table.</p>
     *
     * <p><strong>Security note</strong>: in production this endpoint must be
     * protected by strong authentication and authorization controls.</p>
     *
     * @param name the unique credential name
     * @return 200 OK with the secret key-value map, 404 Not Found, or 500 if Vault is unavailable
     */
    @GetMapping("/{name}/secret")
    public ResponseEntity<?> getSecret(@PathVariable String name) {
        try {
            Map<String, Object> secretData = credentialService.getSecretByName(name);
            return ResponseEntity.ok(secretData);
        } catch (IllegalArgumentException ex) {
            // No metadata registered for this name
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            // Metadata exists but Vault data is missing (out-of-sync state)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    // =========================================================================
    // DELETE /api/credentials/{name} — Delete credential from Vault + metadata
    // =========================================================================

    /**
     * Deletes a credential: soft-deletes the secret in Vault and removes
     * the metadata record from the database.
     *
     * @param name the unique credential name
     * @return 204 No Content on success, or 404 Not Found if not registered
     */
    @DeleteMapping("/{name}")
    public ResponseEntity<?> deleteCredential(@PathVariable String name) {
        try {
            credentialService.deleteCredential(name);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
