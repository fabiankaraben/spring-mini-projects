package com.example.reactivemongodbapi.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Domain entity representing a book stored in the MongoDB {@code books} collection.
 *
 * <p>MongoDB annotation notes:
 * <ul>
 *   <li>{@link Document} maps this class to a MongoDB collection. The {@code collection}
 *       attribute specifies the collection name. Without it, MongoDB uses the class name
 *       (lower-cased) by default. MongoDB creates the collection automatically if it does
 *       not exist — no schema creation step like SQL {@code CREATE TABLE} is needed.</li>
 *   <li>{@link Id} marks the primary key field. In MongoDB this is always the special
 *       {@code _id} field. Spring Data MongoDB maps a Java {@link String} field annotated
 *       with {@code @Id} to MongoDB's {@code ObjectId} automatically — the driver converts
 *       a 24-hex-character string to/from {@code ObjectId} transparently.</li>
 *   <li>{@link Indexed} creates a MongoDB index on the annotated field. {@code unique = true}
 *       enforces a unique constraint, similar to a UNIQUE index in SQL. Indexes are created
 *       automatically at startup when {@code spring.data.mongodb.auto-index-creation=true}.</li>
 *   <li>{@link Field} explicitly maps a Java field name to a different MongoDB document
 *       field name. This is the MongoDB equivalent of {@code @Column} in JPA / R2DBC.</li>
 *   <li>{@link CreatedDate} / {@link LastModifiedDate} are Spring Data auditing annotations.
 *       They require enabling auditing via {@code @EnableReactiveMongoAuditing}
 *       (see {@link com.example.reactivemongodbapi.config.MongoConfig}).</li>
 * </ul>
 *
 * <p><strong>MongoDB vs relational databases — key differences to understand:</strong>
 * <ul>
 *   <li><em>Schema-less</em> — MongoDB documents in the same collection can have different
 *       fields. The {@link Document} class enforces a schema only in Java, not in MongoDB
 *       itself. This enables schema evolution without migration scripts.</li>
 *   <li><em>ObjectId</em> — MongoDB generates a 12-byte {@code ObjectId} as the default
 *       {@code _id} value. It encodes the creation timestamp, machine ID, and a counter,
 *       making it sortable by insertion time and globally unique without a central counter.</li>
 *   <li><em>Embedded documents and arrays</em> — MongoDB natively stores arrays and nested
 *       objects. The {@code genres} field is a {@link List} stored as a BSON array — no join
 *       table is needed, unlike a SQL many-to-many relationship.</li>
 *   <li><em>Reactive driver</em> — the MongoDB Reactive Streams driver returns
 *       {@code Publisher} types, making all I/O non-blocking. Spring Data wraps these in
 *       Project Reactor's {@link reactor.core.publisher.Mono} and
 *       {@link reactor.core.publisher.Flux}.</li>
 * </ul>
 *
 * <p>Why {@link Double} for price?
 * Spring Data MongoDB maps {@link Double} to BSON {@code double} natively, which enables
 * correct numeric range queries ({@code $gte}/{@code $lte}). Using {@link java.math.BigDecimal}
 * without an explicit codec configuration causes Spring Data to serialize the value as a BSON
 * string, breaking all numeric comparisons. For a production system requiring exact decimal
 * arithmetic, configure a {@code MongoCustomConversions} bean with a {@code Decimal128} codec.
 */
@Document(collection = "books")
public class Book {

    /**
     * MongoDB ObjectId stored as a 24-hex-character String.
     *
     * <p>Left {@code null} before persistence; the Reactive MongoDB driver generates an
     * {@code ObjectId} and populates this field after the insert completes. Using
     * {@link String} (rather than {@code ObjectId}) makes serialization to/from JSON
     * straightforward — Jackson serialises it as a plain string.
     */
    @Id
    private String id;

    /**
     * Book title — indexed (non-unique) to speed up title-based searches.
     * The index improves query performance but allows duplicate titles (e.g.,
     * "The Great Gatsby" could appear in multiple editions).
     */
    @Indexed
    private String title;

    /**
     * Author's full name (e.g., "George Orwell").
     * Indexed to speed up author lookups across the collection.
     */
    @Indexed
    private String author;

    /**
     * International Standard Book Number — unique identifier for each edition.
     * {@code unique = true} enforces a UNIQUE constraint in MongoDB, preventing
     * two documents with the same ISBN from being inserted.
     */
    @Indexed(unique = true)
    private String isbn;

    /**
     * Book price in the application's base currency.
     * Stored as BSON {@code double} for correct numeric range query behaviour.
     */
    private Double price;

    /**
     * Year the book was first published (e.g., 1949 for "Nineteen Eighty-Four").
     * Stored as a BSON 32-bit integer.
     */
    @Field("published_year")
    private int publishedYear;

    /**
     * List of genres the book belongs to (e.g., ["fiction", "dystopia"]).
     *
     * <p>MongoDB stores this as a BSON array within the document — no separate
     * join collection is needed. This is a major advantage over relational databases
     * where a many-to-many relationship requires an extra join table.
     */
    private List<String> genres;

    /**
     * Short description / synopsis of the book.
     */
    private String description;

    /**
     * Language the book is written in (e.g., "English", "Spanish").
     */
    private String language;

    /**
     * Number of pages in this edition.
     */
    @Field("page_count")
    private int pageCount;

    /**
     * Whether the book is available for purchase / display on the storefront.
     * Inactive books are hidden from public queries but retained in MongoDB
     * for historical records (soft-delete pattern).
     */
    private boolean available;

    /**
     * Timestamp of when this document was first inserted into MongoDB.
     * Set automatically by Spring Data auditing on the first save.
     */
    @Field("created_at")
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp of the most recent update to this document.
     * Updated automatically by Spring Data auditing on every save.
     */
    @Field("updated_at")
    @LastModifiedDate
    private Instant updatedAt;

    /**
     * No-arg constructor required by Spring Data MongoDB for document mapping.
     * The Reactive MongoDB driver deserializes BSON documents into Java objects
     * using this constructor, then sets each field via reflection or setters.
     */
    public Book() {}

    /**
     * Convenience constructor for creating new books before persistence.
     *
     * <p>The {@code id}, {@code createdAt}, and {@code updatedAt} fields are
     * intentionally omitted — they are assigned by MongoDB and Spring Data auditing
     * respectively after the document is inserted.
     *
     * @param title         book title
     * @param author        author's full name
     * @param isbn          unique ISBN identifier
     * @param price         book price (exact decimal)
     * @param publishedYear year of first publication
     * @param genres        list of genre tags
     * @param description   short synopsis
     * @param language      language the book is written in
     * @param pageCount     number of pages
     * @param available     whether the book is publicly visible
     */
    public Book(String title, String author, String isbn, Double price,
                int publishedYear, List<String> genres, String description,
                String language, int pageCount, boolean available) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.price = price;
        this.publishedYear = publishedYear;
        this.genres = genres;
        this.description = description;
        this.language = language;
        this.pageCount = pageCount;
        this.available = available;
    }

    // ── Getters and setters ────────────────────────────────────────────────────────
    // Standard JavaBean accessors. Spring Data MongoDB uses setters for deserialization;
    // Jackson uses them for JSON serialisation/deserialisation.

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public int getPublishedYear() { return publishedYear; }
    public void setPublishedYear(int publishedYear) { this.publishedYear = publishedYear; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
