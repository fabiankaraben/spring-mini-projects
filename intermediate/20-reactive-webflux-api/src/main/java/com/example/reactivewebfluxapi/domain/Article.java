package com.example.reactivewebfluxapi.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Domain entity representing a news/blog article stored in MongoDB.
 *
 * <p>Annotations:
 * <ul>
 *   <li>{@link Document} maps this class to the MongoDB collection named {@code "articles"}.
 *       Every field becomes a BSON field in the stored document.</li>
 *   <li>{@link Id} marks the {@code id} field as the document's primary key ({@code _id}).
 *       Spring Data MongoDB maps between the Java {@code String} and MongoDB's {@code ObjectId}
 *       BSON type automatically.</li>
 * </ul>
 *
 * <p>Why no {@code @Autowired} or Spring annotations in this class?
 * Domain objects (entities) should be plain Java objects (POJOs). Keeping business
 * data separate from infrastructure concerns (Spring, Jackson, etc.) makes the model
 * easy to test and reason about.
 */
@Document(collection = "articles")
public class Article {

    /**
     * MongoDB-generated unique identifier (_id field).
     * Left {@code null} on creation; MongoDB assigns a hex ObjectId string on insert.
     */
    @Id
    private String id;

    /** Short headline displayed in lists and search results. */
    private String title;

    /** Full article body text. */
    private String content;

    /** Article author's display name. */
    private String author;

    /** Category tag for grouping/filtering (e.g., "technology", "sports"). */
    private String category;

    /** Whether the article has been published and is visible to readers. */
    private boolean published;

    /** Timestamp when the article was first created (set once on insert). */
    private Instant createdAt;

    /** Timestamp of the most recent modification (updated on every write). */
    private Instant updatedAt;

    /**
     * No-arg constructor required by Spring Data MongoDB for BSON deserialisation.
     * MongoDB reads documents from the collection and reconstructs Java objects
     * using this constructor, then sets each field via reflection.
     */
    public Article() {}

    /**
     * Convenience constructor for creating new articles (before they are persisted).
     *
     * <p>Sets both {@code createdAt} and {@code updatedAt} to the current instant.
     * The {@code id} is intentionally left {@code null} so MongoDB generates an
     * ObjectId on the first {@code save()} call.
     *
     * @param title     article headline
     * @param content   full body text
     * @param author    author display name
     * @param category  category tag
     * @param published whether the article is publicly visible
     */
    public Article(String title, String content, String author, String category, boolean published) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = category;
        this.published = published;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Getters and setters ────────────────────────────────────────────────────────
    // Standard JavaBean accessors. Spring Data uses setters for deserialization;
    // Jackson uses them for JSON serialisation/deserialisation.

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
