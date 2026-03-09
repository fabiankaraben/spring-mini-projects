package com.example.methodlevelsecurity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * JPA entity representing a document owned by a {@link User}.
 *
 * <p>This is the central resource in the Document Management API. Method-level
 * security annotations on the service layer enforce who can read, update, and
 * delete documents based on ownership and role:</p>
 * <ul>
 *   <li><strong>Owners</strong> can read, update, and delete their own documents.</li>
 *   <li><strong>Moderators</strong> can read all documents and archive any document.</li>
 *   <li><strong>Admins</strong> have full access to all operations on any document.</li>
 * </ul>
 *
 * <h2>Ownership model</h2>
 * <p>The {@code ownerUsername} field stores the username of the user who created
 * the document. SpEL expressions in {@code @PreAuthorize} and {@code @PostAuthorize}
 * compare {@code authentication.name} against this field to enforce ownership.</p>
 *
 * <h2>Visibility model</h2>
 * <p>A document can be {@code PUBLIC} or {@code PRIVATE}. Public documents are
 * visible to all authenticated users. Private documents are visible only to their
 * owner, moderators, and admins. This is demonstrated with {@code @PostFilter}.</p>
 */
@Entity
@Table(name = "documents")
public class Document {

    /**
     * Auto-generated surrogate primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable title of the document. Must be 1–200 characters.
     */
    @Column(nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String title;

    /**
     * Main body text of the document. Can be large; stored as TEXT in PostgreSQL.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    @NotBlank
    private String content;

    /**
     * Username of the user who created this document.
     *
     * <p>This is a denormalized field (not a foreign key) for simplicity.
     * It is set by the service at creation time and never changed afterwards.
     * SpEL expressions reference it as {@code returnObject.ownerUsername}
     * in {@code @PostAuthorize} checks.</p>
     */
    @Column(nullable = false, length = 50)
    private String ownerUsername;

    /**
     * Visibility of this document.
     * Stored as enum name string: {@code "PUBLIC"} or {@code "PRIVATE"}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Visibility visibility;

    /**
     * UTC timestamp of when this document was created.
     * Set automatically at construction time.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC timestamp of the most recent update.
     * Updated by the service on every save.
     */
    @Column(nullable = false)
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA; do not use in application code. */
    protected Document() {}

    /**
     * Convenience constructor for the service and tests.
     *
     * @param title         document title
     * @param content       document body
     * @param ownerUsername username of the creator
     * @param visibility    PUBLIC or PRIVATE
     */
    public Document(String title, String content, String ownerUsername, Visibility visibility) {
        this.title = title;
        this.content = content;
        this.ownerUsername = ownerUsername;
        this.visibility = visibility;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getOwnerUsername() { return ownerUsername; }

    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }

    public Visibility getVisibility() { return visibility; }

    public void setVisibility(Visibility visibility) { this.visibility = visibility; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Document{id=" + id +
                ", title='" + title + "'" +
                ", ownerUsername='" + ownerUsername + "'" +
                ", visibility=" + visibility + "}";
    }
}
