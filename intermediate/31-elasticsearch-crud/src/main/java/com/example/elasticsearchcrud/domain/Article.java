package com.example.elasticsearchcrud.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;

/**
 * Domain entity representing an article stored in Elasticsearch.
 *
 * <p>Key Elasticsearch-specific annotations used here:
 * <ul>
 *   <li>{@link Document} – marks this class as an Elasticsearch document.
 *       {@code indexName} specifies the Elasticsearch index that stores documents
 *       of this type. With {@code createIndex} defaulting to {@code true}, Spring
 *       Data Elasticsearch automatically creates the index (with the field mappings
 *       derived from the {@link Field} annotations below) at application startup if
 *       it does not already exist. This ensures the index is ready before any
 *       repository operation is called, including in integration tests.</li>
 *   <li>{@link Id} – designates the {@code id} field as the document's primary
 *       key. Elasticsearch will generate a UUID string when a document is saved
 *       without an explicit ID.</li>
 *   <li>{@link Field} – controls how a field is mapped in Elasticsearch:
 *       <ul>
 *         <li>{@code FieldType.Text} – analysed for full-text search (tokenised,
 *             stemmed, lowercased). Use for human-readable content like titles and
 *             body text.</li>
 *         <li>{@code FieldType.Keyword} – stored as-is (not tokenised). Use for
 *             exact-match filtering, aggregations, and sorting (e.g. category, tags).</li>
 *         <li>{@code FieldType.Date} – stored as an ISO-8601 timestamp, used for
 *             range queries on creation/update times.</li>
 *         <li>{@code FieldType.Integer} – stored as a 32-bit integer for numeric
 *             range queries (e.g. view count).</li>
 *       </ul>
 *   </li>
 * </ul>
 */
@Document(indexName = "articles")
public class Article {

    /** Elasticsearch document ID – assigned by Elasticsearch if not provided. */
    @Id
    private String id;

    /**
     * Article title.
     * Mapped as {@code text} so it is analysed (tokenised + stemmed) for full-text search.
     * The {@code copyTo = {"searchAll"}} parameter (if used) would feed this field into a
     * catch-all search field; omitted here for simplicity.
     */
    @Field(type = FieldType.Text)
    private String title;

    /**
     * Article body / content.
     * Also mapped as {@code text} for full-text search.
     */
    @Field(type = FieldType.Text)
    private String content;

    /**
     * Author name.
     * Mapped as {@code keyword} so it can be used for exact-match filtering
     * (e.g. "find all articles by 'Jane Doe'") without tokenisation.
     */
    @Field(type = FieldType.Keyword)
    private String author;

    /**
     * Category tag (e.g. "technology", "health", "finance").
     * Mapped as {@code keyword} for exact-match category filters and aggregations.
     */
    @Field(type = FieldType.Keyword)
    private String category;

    /**
     * Number of times the article has been viewed.
     * Stored as an integer for numeric range queries (e.g. "views > 1000").
     */
    @Field(type = FieldType.Integer)
    private int viewCount;

    /**
     * Timestamp when the article was first created.
     * Stored as an Elasticsearch {@code date} field in ISO-8601 format.
     */
    @Field(type = FieldType.Date)
    private Instant createdAt;

    /**
     * Timestamp when the article was last modified.
     * Updated by {@link com.example.elasticsearchcrud.service.ArticleService} on every update.
     */
    @Field(type = FieldType.Date)
    private Instant updatedAt;

    // ── Constructors ──────────────────────────────────────────────────────────────

    /** Default no-arg constructor required by Spring Data Elasticsearch for deserialisation. */
    public Article() {
    }

    /**
     * Convenience constructor used by the service layer when creating a new article.
     * Automatically sets {@code createdAt} and {@code updatedAt} to the current UTC instant.
     *
     * @param title     article title
     * @param content   article body text
     * @param author    author's full name
     * @param category  category tag
     * @param viewCount initial view count (usually 0)
     */
    public Article(String title, String content, String author, String category, int viewCount) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = category;
        this.viewCount = viewCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Getters and Setters ───────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
