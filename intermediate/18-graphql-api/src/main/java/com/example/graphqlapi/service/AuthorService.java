package com.example.graphqlapi.service;

import com.example.graphqlapi.domain.Author;
import com.example.graphqlapi.dto.AuthorInput;
import com.example.graphqlapi.repository.AuthorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for author-related business logic.
 *
 * <p>Acts as the intermediary between the GraphQL resolvers
 * ({@link com.example.graphqlapi.controller.AuthorController}) and the JPA
 * repository ({@link AuthorRepository}). Centralising logic here keeps the
 * controller thin and makes the service independently testable with mocked
 * repositories (no database or Spring context needed for unit tests).
 *
 * <p>{@code @Transactional} on write methods ensures that each operation runs
 * within a single database transaction. If an exception is thrown mid-operation
 * the transaction is automatically rolled back, preventing partial updates.
 */
@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    /**
     * Constructor injection is preferred over field injection because it:
     * <ul>
     *   <li>Makes dependencies explicit and mandatory.</li>
     *   <li>Allows fields to be declared {@code final} (immutability).</li>
     *   <li>Simplifies unit testing – no reflection or Spring context needed.</li>
     * </ul>
     *
     * @param authorRepository Spring Data JPA repository for authors
     */
    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all authors from the database.
     *
     * @return list of all authors (empty list if none exist)
     */
    @Transactional(readOnly = true)
    public List<Author> findAll() {
        return authorRepository.findAll();
    }

    /**
     * Retrieve a single author by primary key.
     *
     * @param id the author's primary key
     * @return an {@link Optional} containing the author, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Author> findById(Long id) {
        return authorRepository.findById(id);
    }

    /**
     * Search authors by name (case-insensitive, substring match).
     *
     * @param name the name fragment to search for
     * @return list of matching authors
     */
    @Transactional(readOnly = true)
    public List<Author> searchByName(String name) {
        return authorRepository.findByNameContainingIgnoreCase(name);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new author.
     *
     * <p>Maps the incoming {@link AuthorInput} DTO to a new {@link Author}
     * domain entity and delegates to the repository for persistence. The
     * repository's {@code save()} method issues an SQL {@code INSERT} statement
     * and returns the persisted entity with the generated primary key populated.
     *
     * @param input the author data from the GraphQL mutation argument
     * @return the newly created and persisted author
     */
    @Transactional
    public Author create(AuthorInput input) {
        // Map DTO → domain entity; id is null so JPA performs an INSERT
        Author author = new Author(input.getName(), input.getBio());
        return authorRepository.save(author);
    }

    /**
     * Update an existing author's mutable fields.
     *
     * <p>Loads the existing entity first to preserve any fields not included
     * in the input. JPA's dirty-checking mechanism detects changes to the
     * managed entity and issues an SQL {@code UPDATE} when the transaction commits.
     *
     * @param id    the primary key of the author to update
     * @param input the new field values
     * @return an {@link Optional} with the updated author, or empty if not found
     */
    @Transactional
    public Optional<Author> update(Long id, AuthorInput input) {
        Optional<Author> existing = authorRepository.findById(id);
        if (existing.isEmpty()) {
            // Return empty to signal "not found" – caller maps this to a GraphQL error
            return Optional.empty();
        }

        Author author = existing.get();
        // Update only the mutable fields that are part of the input contract
        author.setName(input.getName());
        author.setBio(input.getBio());

        // JPA dirty-checking: no explicit save() needed because author is a managed entity
        // within this transaction. The UPDATE SQL runs when the transaction commits.
        return Optional.of(author);
    }

    /**
     * Delete an author by primary key.
     *
     * <p>Checks for existence first so the caller can distinguish between
     * "deleted successfully" and "author not found".
     *
     * @param id the primary key of the author to delete
     * @return {@code true} if the author existed and was deleted; {@code false} if not found
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (!authorRepository.existsById(id)) {
            return false;
        }
        authorRepository.deleteById(id);
        return true;
    }
}
