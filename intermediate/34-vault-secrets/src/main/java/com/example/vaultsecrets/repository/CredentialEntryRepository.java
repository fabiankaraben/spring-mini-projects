package com.example.vaultsecrets.repository;

import com.example.vaultsecrets.domain.CredentialEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CredentialEntry} metadata.
 *
 * <p>Spring Data automatically provides a full CRUD implementation at runtime.
 * We only need to declare custom finder methods; Spring derives the query from
 * the method name using its "query derivation" mechanism.</p>
 *
 * <h2>What this repository does NOT store</h2>
 * <p>No secret values (passwords, API keys, tokens) are stored here.
 * This repository only tracks the Vault path, name, and description of each
 * credential.  The actual values live exclusively in Vault.</p>
 */
public interface CredentialEntryRepository extends JpaRepository<CredentialEntry, Long> {

    /**
     * Finds a credential entry by its human-readable name.
     *
     * <p>Spring Data derives the SQL {@code SELECT … WHERE name = ?} query from
     * the method name automatically — no {@code @Query} annotation needed.</p>
     *
     * @param name the unique name of the credential entry
     * @return an Optional containing the entry if found, or empty if not
     */
    Optional<CredentialEntry> findByName(String name);

    /**
     * Checks whether a credential entry with the given name already exists.
     *
     * <p>Used to prevent duplicate credential registrations.</p>
     *
     * @param name the name to check
     * @return {@code true} if an entry with that name exists
     */
    boolean existsByName(String name);
}
