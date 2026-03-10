package com.example.jpaauditing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing a blog article.
 *
 * <p>This entity demonstrates JPA Auditing by extending {@link Auditable}.
 * The {@code createdAt} and {@code updatedAt} columns defined in the superclass
 * are automatically managed by Spring Data's {@code AuditingEntityListener} —
 * no manual timestamp code is needed anywhere in the service or repository layer.
 *
 * <p>Lifecycle of the audit fields:
 * <ul>
 *   <li><b>POST /articles</b> — both {@code createdAt} and {@code updatedAt} are
 *       set to the current instant at insert time.</li>
 *   <li><b>PUT /articles/{id}</b> — only {@code updatedAt} is refreshed;
 *       {@code createdAt} stays unchanged because its column is marked
 *       {@code updatable = false}.</li>
 * </ul>
 */
@Entity
@Table(name = "articles")
public class Article extends Auditable {

    /**
     * Primary key. Uses the database's identity/serial strategy so PostgreSQL
     * generates the ID automatically on INSERT.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Title of the article. Must be unique across all articles.
     * {@code nullable = false} enforces a NOT NULL constraint at the DB level.
     */
    @Column(name = "title", nullable = false, unique = true)
    private String title;

    /**
     * Full article body text. Stored as TEXT in PostgreSQL (via {@code columnDefinition}).
     * {@code nullable = false} ensures every article has content.
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Author of the article. This is a plain String field — not an
     * auditing "created-by" field (which would require an {@code AuditorAware} bean).
     * It is simply application-level data set by the client.
     */
    @Column(name = "author", nullable = false)
    private String author;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default no-args constructor required by JPA. */
    protected Article() {
    }

    /**
     * Convenience constructor for creating a new article with all required fields.
     *
     * @param title   article title
     * @param content article body text
     * @param author  article author name
     */
    public Article(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
