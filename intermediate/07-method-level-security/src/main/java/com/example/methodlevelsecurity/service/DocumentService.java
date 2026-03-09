package com.example.methodlevelsecurity.service;

import com.example.methodlevelsecurity.domain.Document;
import com.example.methodlevelsecurity.domain.Visibility;
import com.example.methodlevelsecurity.dto.DocumentRequest;
import com.example.methodlevelsecurity.repository.DocumentRepository;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing documents, showcasing all five method-level security annotations.
 *
 * <h2>Annotations demonstrated in this service</h2>
 *
 * <h3>1. {@code @PreAuthorize}</h3>
 * <p>The most commonly used annotation. Evaluates a SpEL expression
 * <em>before</em> the method body executes. If the expression returns {@code false},
 * Spring Security throws an {@link org.springframework.security.access.AccessDeniedException}
 * and the method body is never reached.
 * SpEL has access to the {@code authentication} object and to method parameters
 * via {@code #paramName} syntax.</p>
 *
 * <h3>2. {@code @PostAuthorize}</h3>
 * <p>Evaluates a SpEL expression <em>after</em> the method returns. The return value
 * is available as {@code returnObject}. Useful for ownership checks where you need
 * to load the resource first to know who owns it.</p>
 *
 * <h3>3. {@code @PreFilter}</h3>
 * <p>Filters a collection <em>parameter</em> before the method runs. Elements for
 * which the SpEL expression evaluates to {@code false} are removed from the
 * collection. The current element is available as {@code filterObject}.
 * Useful for bulk operations where you want to silently discard unauthorised items
 * rather than throw an exception.</p>
 *
 * <h3>4. {@code @PostFilter}</h3>
 * <p>Filters a collection <em>return value</em> after the method returns. Elements
 * for which the expression evaluates to {@code false} are removed. The current
 * element is available as {@code filterObject}. Useful for queries that return
 * many items and you want to silently hide the ones the caller should not see.</p>
 *
 * <h3>5. {@code @Secured}</h3>
 * <p>Demonstrated in {@link UserService}. Simple role-only check; does not support
 * SpEL. Shown here for contrast with the more expressive annotations above.</p>
 *
 * <h2>Ownership-based access (principal-aware SpEL)</h2>
 * <p>The expression {@code authentication.name == #document.ownerUsername}
 * or {@code returnObject.ownerUsername == authentication.name} compares the
 * currently authenticated username against the document's owner field.
 * This is the canonical way to implement per-resource ownership checks in
 * Spring Security without writing custom permission evaluators.</p>
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new document owned by the currently authenticated user.
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize("isAuthenticated()")} –
     * any authenticated user (regardless of role) can create documents.
     * The owner is set to {@code authentication.name} inside the method body.</p>
     *
     * @param request DTO with title, content, and visibility
     * @return the persisted {@link Document}
     */
    @PreAuthorize("isAuthenticated()")
    public Document createDocument(DocumentRequest request) {
        // The currently authenticated user becomes the document owner.
        String currentUser = getCurrentUsername();

        // Parse the visibility; default to PRIVATE if absent or invalid.
        Visibility visibility = parseVisibility(request.getVisibility());

        Document doc = new Document(
                request.getTitle(),
                request.getContent(),
                currentUser,
                visibility
        );
        return documentRepository.save(doc);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns a single document by ID.
     *
     * <p><strong>Security: {@code @PostAuthorize}</strong> –
     * the document is loaded first, then the expression is evaluated against it.
     * The expression checks two conditions (OR):</p>
     * <ol>
     *   <li>The caller is the document's owner
     *       ({@code returnObject.ownerUsername == authentication.name}).</li>
     *   <li>The document is PUBLIC and the caller is authenticated.</li>
     *   <li>The caller has ROLE_ADMIN or ROLE_MODERATOR.</li>
     * </ol>
     *
     * <p><strong>Why @PostAuthorize instead of @PreAuthorize here?</strong>
     * We need to inspect the actual document's {@code ownerUsername} and
     * {@code visibility} to decide if access is allowed. Those fields are only
     * available <em>after</em> the database load, so @PostAuthorize is the
     * correct choice.</p>
     *
     * @param id the document's primary key
     * @return the found {@link Document}
     * @throws IllegalArgumentException if no document with that ID exists
     */
    @PostAuthorize(
        "returnObject.ownerUsername == authentication.name " +
        "or returnObject.visibility.name() == 'PUBLIC' " +
        "or hasAnyRole('ADMIN', 'MODERATOR')"
    )
    public Document getDocumentById(Long id) {
        // The method body loads the document unconditionally.
        // @PostAuthorize will check the result AFTER this returns.
        // If the expression is false, AccessDeniedException is thrown
        // and the caller never receives the document.
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found with id: " + id));
    }

    /**
     * Returns all documents owned by the currently authenticated user.
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize("isAuthenticated()")} –
     * any authenticated user can retrieve their own documents. The owner filter
     * is applied inside the query, so each user only sees their own list.</p>
     *
     * @return list of documents owned by the current user
     */
    @PreAuthorize("isAuthenticated()")
    public List<Document> getMyDocuments() {
        // Filter by the currently authenticated user's name at the query level.
        return documentRepository.findByOwnerUsername(getCurrentUsername());
    }

    /**
     * Returns all PUBLIC documents from all users.
     *
     * <p><strong>Security: {@code @PostFilter}</strong> –
     * the repository returns all PUBLIC documents, and then
     * Spring Security's {@code @PostFilter} runs the SpEL expression on each
     * element. Elements for which the expression evaluates to {@code false} are
     * removed before the list is returned to the caller.</p>
     *
     * <p>In this case the filter ensures that only public documents are returned
     * (the DB query already filters this, but the annotation demonstrates the
     * pattern). The expression is: the document is PUBLIC <em>or</em> the caller
     * has ROLE_ADMIN or ROLE_MODERATOR (who can see everything).</p>
     *
     * <p><strong>Important:</strong> {@code @PostFilter} works on the actual Java
     * {@link List} returned by the method. It modifies the list in place before
     * handing it back. The collection must be mutable (not {@link java.util.List#of}).</p>
     *
     * @return a filtered list of public documents
     */
    @PostFilter(
        "filterObject.visibility.name() == 'PUBLIC' " +
        "or hasAnyRole('ADMIN', 'MODERATOR')"
    )
    public List<Document> getPublicDocuments() {
        // Return all documents; @PostFilter removes the ones the expression rejects.
        // We return a mutable ArrayList because @PostFilter removes elements in place.
        return new ArrayList<>(documentRepository.findAll());
    }

    /**
     * Returns all documents (for admin/moderator use).
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")} –
     * only privileged users may request the full unfiltered list.</p>
     *
     * @return all documents in the database
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Updates a document's title, content, and/or visibility.
     *
     * <p><strong>Security: {@code @PreAuthorize} with ownership OR role check:</strong>
     * the SpEL expression uses {@code @documentRepository} (a Spring bean reference
     * inside SpEL) to load the document and check ownership – all before the method
     * body runs. This avoids the need to load the document twice (once for the check
     * and once for the update) in some patterns, though here we do load it again in
     * the body to perform the actual update.</p>
     *
     * <p>Alternative approach demonstrated here: we use a combined expression that
     * allows access if:</p>
     * <ul>
     *   <li>The caller is ADMIN, OR</li>
     *   <li>The caller is the document's owner (checked by the method body).</li>
     * </ul>
     *
     * <p>The ownership check inside the method body is an explicit guard for the
     * non-admin case, showing how to combine annotation and programmatic checks.</p>
     *
     * @param id      the document's primary key
     * @param request DTO with updated title, content, visibility
     * @return the updated {@link Document}
     * @throws IllegalArgumentException  if the document is not found
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         caller is not the owner and not an admin
     */
    @PreAuthorize("isAuthenticated()")
    public Document updateDocument(Long id, DocumentRequest request) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found with id: " + id));

        // Ownership check: only the owner or an admin/moderator may update.
        String currentUser = getCurrentUsername();
        boolean isOwner = doc.getOwnerUsername().equals(currentUser);
        boolean isPrivileged = hasRole("ROLE_ADMIN") || hasRole("ROLE_MODERATOR");

        if (!isOwner && !isPrivileged) {
            // Throw the same exception Spring Security uses for @PreAuthorize denials.
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to update this document");
        }

        // Apply updates
        doc.setTitle(request.getTitle());
        doc.setContent(request.getContent());
        if (request.getVisibility() != null) {
            doc.setVisibility(parseVisibility(request.getVisibility()));
        }
        doc.setUpdatedAt(Instant.now());

        return documentRepository.save(doc);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a document.
     *
     * <p><strong>Security: {@code @PreAuthorize} with ownership check via SpEL
     * and method parameter:</strong>
     * The expression {@code #id} refers to the method parameter named {@code id}.
     * We cannot check ownership in pure SpEL without a bean reference or a custom
     * {@link org.springframework.security.access.PermissionEvaluator}, so here we
     * demonstrate a combined approach: {@code @PreAuthorize} enforces that the user
     * is authenticated, and the method body performs the ownership check.</p>
     *
     * @param id the document's primary key
     * @throws IllegalArgumentException if the document is not found
     * @throws org.springframework.security.access.AccessDeniedException if the
     *         caller is not the owner and not an admin
     */
    @PreAuthorize("isAuthenticated()")
    public void deleteDocument(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found with id: " + id));

        // Only the owner or an admin may delete a document.
        String currentUser = getCurrentUsername();
        boolean isOwner = doc.getOwnerUsername().equals(currentUser);
        boolean isAdmin = hasRole("ROLE_ADMIN");

        if (!isOwner && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to delete this document");
        }

        documentRepository.deleteById(id);
    }

    // ── Bulk operations with @PreFilter ───────────────────────────────────────

    /**
     * Bulk-creates documents. Only documents where the caller is set as owner
     * are saved (demonstrating {@code @PreFilter}).
     *
     * <p><strong>Security: {@code @PreFilter}</strong> –
     * before the method runs, Spring Security iterates the {@code documents} list.
     * For each element ({@code filterObject}) it evaluates the expression. Elements
     * for which it returns {@code false} are silently removed from the list.
     * This means only documents owned by the currently authenticated user are
     * passed to the {@code saveAll} call.</p>
     *
     * <p>{@code filterTarget = "documents"} specifies which parameter to filter
     * when the method has multiple parameters.</p>
     *
     * @param documents mutable list of documents to save; items with mismatched
     *                  {@code ownerUsername} are silently discarded by the filter
     * @return list of actually persisted documents (only those the filter passed)
     */
    @PreAuthorize("isAuthenticated()")
    @PreFilter(
        value = "filterObject.ownerUsername == authentication.name or hasRole('ADMIN')",
        filterTarget = "documents"
    )
    public List<Document> bulkCreateDocuments(List<Document> documents) {
        // At this point, @PreFilter has already removed any document
        // whose ownerUsername does not match the current user (unless ADMIN).
        return documentRepository.saveAll(documents);
    }

    // ── Admin-only ────────────────────────────────────────────────────────────

    /**
     * Archives (hard-deletes) all documents owned by a given user.
     *
     * <p><strong>Security:</strong> {@code @PreAuthorize("hasRole('ADMIN')")} –
     * only admins can remove all documents for a user. The expression also shows
     * the {@code #username} parameter binding: the method parameter {@code username}
     * is accessible in SpEL as {@code #username}.</p>
     *
     * @param username the username whose documents to delete
     * @return number of documents deleted
     */
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #username")
    public int deleteAllDocumentsOf(String username) {
        List<Document> docs = documentRepository.findByOwnerUsername(username);
        documentRepository.deleteAll(docs);
        return docs.size();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the username of the currently authenticated principal.
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    /**
     * Checks whether the current user has a specific granted authority.
     *
     * @param role the full role string, e.g. {@code "ROLE_ADMIN"}
     * @return {@code true} if the current user has that authority
     */
    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    /**
     * Parses a visibility string to a {@link Visibility} enum constant.
     * Defaults to {@link Visibility#PRIVATE} if the string is null, blank, or invalid.
     *
     * @param visibilityString raw string from the request (may be null)
     * @return the parsed {@link Visibility}, or {@code PRIVATE} as safe default
     */
    private Visibility parseVisibility(String visibilityString) {
        if (visibilityString == null || visibilityString.isBlank()) {
            return Visibility.PRIVATE;
        }
        try {
            return Visibility.valueOf(visibilityString.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Visibility.PRIVATE;
        }
    }
}
