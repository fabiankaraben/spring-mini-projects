package com.example.methodlevelsecurity.service;

import com.example.methodlevelsecurity.domain.Document;
import com.example.methodlevelsecurity.domain.Visibility;
import com.example.methodlevelsecurity.dto.DocumentRequest;
import com.example.methodlevelsecurity.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentService}.
 *
 * <h2>Testing strategy</h2>
 * <p>These tests use Mockito to replace the real {@link DocumentRepository} with a
 * test double, so no database or Spring context is needed. Each test runs in
 * milliseconds.</p>
 *
 * <h2>SecurityContext setup</h2>
 * <p>{@code @PreAuthorize} and {@code @PostAuthorize} are enforced by Spring Security's
 * CGLIB proxy. Since we instantiate {@link DocumentService} directly (not via Spring),
 * the proxy is absent. We therefore test the <em>business logic</em> by manually
 * populating the {@link SecurityContextHolder} with a fake {@link org.springframework.security.core.Authentication}.
 * This lets us call methods that read {@code authentication.name} or check roles via
 * {@link SecurityContextHolder#getContext()}.</p>
 *
 * <p>The security enforcement ({@code @PreAuthorize} / {@code @PostAuthorize} denials)
 * is verified in the integration test class where the full Spring context is loaded.</p>
 *
 * <h2>@ExtendWith(MockitoExtension.class)</h2>
 * <p>Activates Mockito annotation processing ({@code @Mock}, {@code @InjectMocks})
 * without starting a Spring context.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService unit tests")
class DocumentServiceTest {

    /** Mocked repository – no DB calls are made. */
    @Mock
    private DocumentRepository documentRepository;

    /** The system under test. Mockito injects the mock above via constructor injection. */
    @InjectMocks
    private DocumentService documentService;

    // ── SecurityContext helpers ───────────────────────────────────────────────

    /**
     * Populates the {@link SecurityContextHolder} with a fake authentication token
     * so that calls to {@code SecurityContextHolder.getContext().getAuthentication()}
     * inside {@link DocumentService} return a predictable value.
     *
     * @param username  the principal name to set
     * @param roleValue the granted authority string (e.g. "ROLE_USER")
     */
    private void setAuthentication(String username, String roleValue) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        username, null,
                        List.of(new SimpleGrantedAuthority(roleValue))
                );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    /** Clears the SecurityContext after each test to avoid cross-test contamination. */
    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Helper factories ──────────────────────────────────────────────────────

    /**
     * Builds a {@link DocumentRequest} DTO for use in tests.
     */
    private DocumentRequest request(String title, String content, String visibility) {
        DocumentRequest req = new DocumentRequest();
        req.setTitle(title);
        req.setContent(content);
        req.setVisibility(visibility);
        return req;
    }

    /**
     * Builds a {@link Document} with the given owner.
     */
    private Document doc(Long id, String title, String owner, Visibility visibility) {
        Document d = new Document(title, "Some content", owner, visibility);
        // Simulate a persisted entity by setting the id via reflection would be complex;
        // instead we return it as-is and let the mock return it with any id needed.
        return d;
    }

    // ── createDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("createDocument: should save a document owned by the current user")
    void createDocument_savesDocumentWithCurrentUserAsOwner() {
        // Arrange: simulate "alice" is logged in as ROLE_USER
        setAuthentication("alice", "ROLE_USER");
        DocumentRequest req = request("My Note", "Hello world", "PRIVATE");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Document result = documentService.createDocument(req);

        // Assert: owner must be set to the current authenticated user
        assertEquals("alice", result.getOwnerUsername(),
                "The document owner must match the authenticated user");
        assertEquals("My Note", result.getTitle());
        assertEquals(Visibility.PRIVATE, result.getVisibility());

        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("createDocument: should default visibility to PRIVATE when not specified")
    void createDocument_defaultsVisibilityToPrivate() {
        setAuthentication("bob", "ROLE_USER");
        DocumentRequest req = request("Draft", "Content", null); // no visibility
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document result = documentService.createDocument(req);

        assertEquals(Visibility.PRIVATE, result.getVisibility(),
                "Visibility should default to PRIVATE when not provided");
    }

    @Test
    @DisplayName("createDocument: should accept PUBLIC visibility")
    void createDocument_acceptsPublicVisibility() {
        setAuthentication("carol", "ROLE_USER");
        DocumentRequest req = request("Public Post", "Read me", "PUBLIC");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document result = documentService.createDocument(req);

        assertEquals(Visibility.PUBLIC, result.getVisibility());
    }

    // ── getDocumentById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDocumentById: should return document when found")
    void getDocumentById_returnsDocumentWhenFound() {
        setAuthentication("alice", "ROLE_USER");
        Document stored = doc(1L, "Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(stored));

        Document result = documentService.getDocumentById(1L);

        assertEquals("Note", result.getTitle());
        assertEquals("alice", result.getOwnerUsername());
    }

    @Test
    @DisplayName("getDocumentById: should throw IllegalArgumentException when not found")
    void getDocumentById_throwsWhenNotFound() {
        setAuthentication("alice", "ROLE_USER");
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> documentService.getDocumentById(99L));
    }

    // ── getMyDocuments ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyDocuments: should return only documents owned by the current user")
    void getMyDocuments_returnsCurrentUserDocuments() {
        setAuthentication("alice", "ROLE_USER");
        List<Document> aliceDocs = List.of(
                doc(1L, "Note 1", "alice", Visibility.PRIVATE),
                doc(2L, "Note 2", "alice", Visibility.PUBLIC)
        );
        when(documentRepository.findByOwnerUsername("alice")).thenReturn(aliceDocs);

        List<Document> result = documentService.getMyDocuments();

        assertEquals(2, result.size());
        verify(documentRepository).findByOwnerUsername("alice");
    }

    // ── getPublicDocuments ────────────────────────────────────────────────────

    @Test
    @DisplayName("getPublicDocuments: should return all documents from repository (filter handled by @PostFilter in integration)")
    void getPublicDocuments_returnsAllFromRepository() {
        setAuthentication("alice", "ROLE_USER");
        // @PostFilter is a Spring Security proxy concern – not tested here.
        // We test that the method delegates to findAll().
        List<Document> all = new ArrayList<>(List.of(
                doc(1L, "Public Doc", "bob", Visibility.PUBLIC),
                doc(2L, "Private Doc", "carol", Visibility.PRIVATE)
        ));
        when(documentRepository.findAll()).thenReturn(all);

        List<Document> result = documentService.getPublicDocuments();

        // Without Spring Security proxy, @PostFilter is not evaluated, so all items are returned.
        assertEquals(2, result.size());
        verify(documentRepository).findAll();
    }

    // ── getAllDocuments ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllDocuments: should delegate to findAll")
    void getAllDocuments_delegatesToFindAll() {
        setAuthentication("admin", "ROLE_ADMIN");
        List<Document> all = List.of(
                doc(1L, "Doc A", "alice", Visibility.PUBLIC),
                doc(2L, "Doc B", "bob", Visibility.PRIVATE)
        );
        when(documentRepository.findAll()).thenReturn(all);

        List<Document> result = documentService.getAllDocuments();

        assertEquals(2, result.size());
        verify(documentRepository).findAll();
    }

    // ── updateDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: owner can update their own document")
    void updateDocument_ownerCanUpdate() {
        setAuthentication("alice", "ROLE_USER");
        Document existing = doc(1L, "Old Title", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentRequest req = request("New Title", "New content", "PUBLIC");
        Document result = documentService.updateDocument(1L, req);

        assertEquals("New Title", result.getTitle());
        assertEquals("New content", result.getContent());
        assertEquals(Visibility.PUBLIC, result.getVisibility());
        verify(documentRepository).save(existing);
    }

    @Test
    @DisplayName("updateDocument: admin can update any document")
    void updateDocument_adminCanUpdateAnyDocument() {
        setAuthentication("admin", "ROLE_ADMIN");
        Document existing = doc(1L, "Alice Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentRequest req = request("Updated", "Updated content", "PUBLIC");
        Document result = documentService.updateDocument(1L, req);

        assertEquals("Updated", result.getTitle());
    }

    @Test
    @DisplayName("updateDocument: non-owner non-admin should throw AccessDeniedException")
    void updateDocument_nonOwnerThrowsAccessDenied() {
        setAuthentication("bob", "ROLE_USER"); // bob is NOT alice (the owner)
        Document existing = doc(1L, "Alice Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));

        DocumentRequest req = request("Hacked Title", "Hacked content", "PUBLIC");
        assertThrows(AccessDeniedException.class,
                () -> documentService.updateDocument(1L, req));

        // Repository save must NOT be called if access is denied
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDocument: should throw IllegalArgumentException when document not found")
    void updateDocument_throwsWhenDocumentNotFound() {
        setAuthentication("alice", "ROLE_USER");
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> documentService.updateDocument(99L, request("T", "C", "PUBLIC")));
    }

    // ── deleteDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteDocument: owner can delete their own document")
    void deleteDocument_ownerCanDelete() {
        setAuthentication("alice", "ROLE_USER");
        Document existing = doc(1L, "Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));

        documentService.deleteDocument(1L);

        verify(documentRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteDocument: admin can delete any document")
    void deleteDocument_adminCanDeleteAnyDocument() {
        setAuthentication("admin", "ROLE_ADMIN");
        Document existing = doc(1L, "Alice Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));

        documentService.deleteDocument(1L);

        verify(documentRepository).deleteById(1L);
    }

    @Test
    @DisplayName("deleteDocument: non-owner non-admin should throw AccessDeniedException")
    void deleteDocument_nonOwnerThrowsAccessDenied() {
        setAuthentication("bob", "ROLE_USER");
        Document existing = doc(1L, "Alice Note", "alice", Visibility.PRIVATE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(existing));

        assertThrows(AccessDeniedException.class,
                () -> documentService.deleteDocument(1L));

        verify(documentRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteDocument: should throw IllegalArgumentException when not found")
    void deleteDocument_throwsWhenNotFound() {
        setAuthentication("alice", "ROLE_USER");
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> documentService.deleteDocument(99L));
    }

    // ── deleteAllDocumentsOf ──────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAllDocumentsOf: should delete all documents of the given user")
    void deleteAllDocumentsOf_deletesAllUserDocuments() {
        setAuthentication("admin", "ROLE_ADMIN");
        List<Document> docs = List.of(
                doc(1L, "Doc 1", "alice", Visibility.PRIVATE),
                doc(2L, "Doc 2", "alice", Visibility.PUBLIC)
        );
        when(documentRepository.findByOwnerUsername("alice")).thenReturn(docs);

        int count = documentService.deleteAllDocumentsOf("alice");

        assertEquals(2, count, "Should return the count of deleted documents");
        verify(documentRepository).deleteAll(docs);
    }

    @Test
    @DisplayName("deleteAllDocumentsOf: returns 0 when user has no documents")
    void deleteAllDocumentsOf_returnsZeroWhenNoDocs() {
        setAuthentication("alice", "ROLE_USER");
        when(documentRepository.findByOwnerUsername("alice")).thenReturn(List.of());

        int count = documentService.deleteAllDocumentsOf("alice");

        assertEquals(0, count);
    }
}
