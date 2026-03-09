package com.example.methodlevelsecurity.controller;

import com.example.methodlevelsecurity.domain.Document;
import com.example.methodlevelsecurity.dto.DocumentRequest;
import com.example.methodlevelsecurity.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing document management endpoints.
 *
 * <h2>Design note: security enforcement location</h2>
 * <p>All security decisions are delegated to the {@link DocumentService}, which
 * carries the {@code @PreAuthorize}, {@code @PostAuthorize}, {@code @PreFilter},
 * and {@code @PostFilter} annotations. The controller intentionally has <em>no</em>
 * security annotations – it is kept thin and acts as an HTTP adapter only.</p>
 *
 * <p>This pattern illustrates the key benefit of method-level security at the
 * <em>service</em> layer: the security rules are enforced regardless of whether
 * the service is called via HTTP, a batch job, or another service.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST   /api/documents}          – create a new document (any auth user)</li>
 *   <li>{@code GET    /api/documents/me}        – list my own documents (any auth user)</li>
 *   <li>{@code GET    /api/documents/public}    – list all public documents (@PostFilter demo)</li>
 *   <li>{@code GET    /api/documents}           – list all documents (admin/moderator only)</li>
 *   <li>{@code GET    /api/documents/{id}}      – get a document by ID (@PostAuthorize demo)</li>
 *   <li>{@code PUT    /api/documents/{id}}      – update a document (owner or admin)</li>
 *   <li>{@code DELETE /api/documents/{id}}      – delete a document (owner or admin)</li>
 *   <li>{@code DELETE /api/documents/user/{u}}  – delete all documents of a user (admin or self)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Creates a new document. The authenticated user becomes the owner.
     *
     * <p>Security is enforced by {@link DocumentService#createDocument} via
     * {@code @PreAuthorize("isAuthenticated()")}.</p>
     *
     * <p><b>Request body example:</b></p>
     * <pre>{@code
     * { "title": "My Note", "content": "Hello world", "visibility": "PRIVATE" }
     * }</pre>
     *
     * @param request validated document DTO
     * @return 201 Created with the saved document
     */
    @PostMapping
    public ResponseEntity<Document> createDocument(@Valid @RequestBody DocumentRequest request) {
        Document saved = documentService.createDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns all documents owned by the currently authenticated user.
     *
     * <p>Security enforced by {@link DocumentService#getMyDocuments()}.</p>
     *
     * @return 200 OK with the list of the caller's own documents
     */
    @GetMapping("/me")
    public ResponseEntity<List<Document>> getMyDocuments() {
        return ResponseEntity.ok(documentService.getMyDocuments());
    }

    /**
     * Returns all PUBLIC documents. {@code @PostFilter} in the service silently
     * removes private documents for non-admin/non-moderator callers.
     *
     * <p>Security enforced by {@link DocumentService#getPublicDocuments()} via
     * {@code @PostFilter}.</p>
     *
     * @return 200 OK with the filtered list of public documents
     */
    @GetMapping("/public")
    public ResponseEntity<List<Document>> getPublicDocuments() {
        return ResponseEntity.ok(documentService.getPublicDocuments());
    }

    /**
     * Returns all documents (admin/moderator only).
     *
     * <p>Security enforced by {@link DocumentService#getAllDocuments()} via
     * {@code @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")}.</p>
     *
     * @return 200 OK with all documents, or 403 Forbidden for regular users
     */
    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentService.getAllDocuments());
    }

    /**
     * Returns a document by ID. Access is determined by {@code @PostAuthorize}
     * in the service: owner, public doc, or admin/moderator.
     *
     * @param id the document's primary key
     * @return 200 OK with the document, 403 Forbidden, or 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(documentService.getDocumentById(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Updates a document. Only the owner, admin, or moderator may update.
     *
     * <p>Security enforced by {@link DocumentService#updateDocument} programmatically.</p>
     *
     * @param id      the document's primary key
     * @param request validated update DTO
     * @return 200 OK with the updated document, 403 Forbidden, or 404 Not Found
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDocument(@PathVariable Long id,
                                            @Valid @RequestBody DocumentRequest request) {
        try {
            return ResponseEntity.ok(documentService.updateDocument(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a document. Only the owner or admin may delete.
     *
     * <p>Security enforced by {@link DocumentService#deleteDocument} programmatically.</p>
     *
     * @param id the document's primary key
     * @return 200 OK on success, 403 Forbidden, or 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id) {
        try {
            documentService.deleteDocument(id);
            return ResponseEntity.ok(Map.of("message", "Document " + id + " deleted"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Deletes all documents owned by the given username.
     * Only the user themselves or an admin may call this.
     *
     * <p>Security enforced by {@link DocumentService#deleteAllDocumentsOf} via
     * {@code @PreAuthorize("hasRole('ADMIN') or authentication.name == #username")}.</p>
     *
     * @param username the username whose documents to delete
     * @return 200 OK with the count of deleted documents, or 403 Forbidden
     */
    @DeleteMapping("/user/{username}")
    public ResponseEntity<?> deleteAllDocumentsOf(@PathVariable String username) {
        int count = documentService.deleteAllDocumentsOf(username);
        return ResponseEntity.ok(Map.of(
                "message", "Deleted " + count + " document(s) for user '" + username + "'"
        ));
    }
}
