package com.example.dockercomposesupport.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * JPA entity representing a book in the catalogue.
 *
 * <p>This is the core domain object of the mini-project. It maps to the
 * {@code books} table in PostgreSQL, which is created automatically by
 * Hibernate via {@code spring.jpa.hibernate.ddl-auto=update}.</p>
 *
 * <h2>Annotations used</h2>
 * <ul>
 *   <li>{@code @Entity} — marks this class as a JPA-managed persistent entity.</li>
 *   <li>{@code @Table} — maps to the {@code books} table explicitly.</li>
 *   <li>{@code @Id} + {@code @GeneratedValue} — uses PostgreSQL's SERIAL / SEQUENCE
 *       strategy to auto-assign primary key values.</li>
 *   <li>{@code @Column(nullable = false, unique = true)} — enforces DB-level constraints
 *       in addition to Bean Validation annotations.</li>
 *   <li>{@code @PrePersist} / {@code @PreUpdate} — lifecycle callbacks that
 *       automatically manage {@code createdAt} and {@code updatedAt} timestamps.</li>
 * </ul>
 */
@Entity
@Table(name = "books")
public class Book {

    /**
     * Primary key.
     * Uses the SEQUENCE generation strategy, which is the default for PostgreSQL.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Book title — required and must be unique across the catalogue.
     * The unique constraint is enforced at both the validation and DB levels.
     */
    @NotBlank(message = "Title must not be blank")
    @Column(nullable = false, unique = true)
    private String title;

    /**
     * Author name — required.
     */
    @NotBlank(message = "Author must not be blank")
    @Column(nullable = false)
    private String author;

    /**
     * International Standard Book Number (ISBN-13 or ISBN-10).
     * Must be unique across the catalogue.
     */
    @NotBlank(message = "ISBN must not be blank")
    @Column(nullable = false, unique = true)
    private String isbn;

    /**
     * Publication year — must be a positive integer (e.g. 2024).
     * Optional field.
     */
    @Min(value = 1, message = "Publication year must be a positive number")
    private Integer publicationYear;

    /**
     * Short description or summary of the book. Optional field.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Timestamp set automatically when the record is first persisted.
     * Managed by the {@link #onPrePersist()} lifecycle callback.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Timestamp updated automatically on every modification.
     * Managed by the {@link #onPreUpdate()} lifecycle callback.
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // JPA lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Called by JPA before the entity is first saved to the database.
     * Sets both {@code createdAt} and {@code updatedAt} to the current time.
     */
    @PrePersist
    void onPrePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Called by JPA before every subsequent update of this entity.
     * Only {@code updatedAt} is refreshed; {@code createdAt} is left unchanged
     * because it is marked {@code updatable = false} at the column level.
     */
    @PreUpdate
    void onPreUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Required no-arg constructor for JPA. */
    protected Book() {}

    /** Convenience constructor used in tests and service layer. */
    public Book(String title, String author, String isbn, Integer publicationYear, String description) {
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.publicationYear = publicationYear;
        this.description = description;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public Integer getPublicationYear() { return publicationYear; }
    public void setPublicationYear(Integer publicationYear) { this.publicationYear = publicationYear; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
