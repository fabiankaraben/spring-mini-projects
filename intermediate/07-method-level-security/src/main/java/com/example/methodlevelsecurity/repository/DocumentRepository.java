package com.example.methodlevelsecurity.repository;

import com.example.methodlevelsecurity.domain.Document;
import com.example.methodlevelsecurity.domain.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Document} entities.
 *
 * <p>Spring Data auto-generates the implementation from the method signatures below.
 * These finder methods are used by the service layer; security rules (ownership checks,
 * role checks) are enforced by method-level annotations on the service, not here.</p>
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Returns all documents owned by the given username.
     *
     * <p>Used by {@link com.example.methodlevelsecurity.service.DocumentService#getMyDocuments()}
     * to load documents for the currently authenticated user.</p>
     *
     * @param ownerUsername the username whose documents to retrieve
     * @return list of documents owned by that user, empty if none
     */
    List<Document> findByOwnerUsername(String ownerUsername);

    /**
     * Returns all documents with the specified visibility level.
     *
     * <p>Used to demonstrate {@code @PostFilter}: the service loads all public
     * documents and then Spring Security's {@code @PostFilter} removes any that
     * the current user should not see (based on custom SpEL logic).</p>
     *
     * @param visibility the visibility level to filter by
     * @return list of documents matching the visibility
     */
    List<Document> findByVisibility(Visibility visibility);

    /**
     * Returns all documents owned by the given user with the specified visibility.
     *
     * @param ownerUsername the username to filter by
     * @param visibility    the visibility level to filter by
     * @return matching documents
     */
    List<Document> findByOwnerUsernameAndVisibility(String ownerUsername, Visibility visibility);
}
