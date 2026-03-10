package com.example.graphqlapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA entity representing a book in the library domain.
 *
 * <p>This entity maps to the {@code books} table in PostgreSQL. It is the
 * "many" side of a many-to-one relationship with {@link Author}: many books
 * can belong to a single author.
 *
 * <p>In the GraphQL schema, {@code Book} is a type that exposes its
 * {@code author} field as a nested object, allowing GraphQL clients to
 * request author details within the same query as book details.
 */
@Entity
@Table(name = "books")
public class Book {

    /**
     * Primary key – auto-incremented by the PostgreSQL sequence.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Title of the book. Cannot be null.
     */
    @Column(nullable = false)
    private String title;

    /**
     * International Standard Book Number (ISBN-13 format recommended).
     * Unique per book to avoid duplicate entries.
     */
    @Column(nullable = false, unique = true)
    private String isbn;

    /**
     * Date the book was first published. Stored as PostgreSQL {@code DATE}.
     * Nullable to accommodate books whose publication date is unknown.
     */
    @Column(name = "published_date")
    private LocalDate publishedDate;

    /**
     * Genre/category of the book (e.g. "Science Fiction", "History").
     */
    @Column
    private String genre;

    /**
     * The author who wrote this book.
     *
     * <p>{@link FetchType#LAZY} is the JPA default for {@code @ManyToOne} but
     * we declare it explicitly for clarity. Lazy loading means the author is
     * only fetched from the database when {@code getAuthor()} is first called.
     * Spring for GraphQL resolves nested fields on demand, so this is efficient:
     * if a query only asks for book fields (not the author), no extra SQL is issued.
     *
     * <p>{@code @JoinColumn(name = "author_id")} specifies the foreign-key column
     * name in the {@code books} table. Without this annotation JPA would default
     * to {@code author_id} as well, but we declare it explicitly for documentation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    /** Required no-arg constructor for JPA. */
    protected Book() {
    }

    /**
     * Creates a new Book with the required fields.
     *
     * @param title         the book title
     * @param isbn          the unique ISBN
     * @param publishedDate the date of first publication (may be {@code null})
     * @param genre         the book genre (may be {@code null})
     * @param author        the author of this book (may be {@code null} if unknown)
     */
    public Book(String title, String isbn, LocalDate publishedDate, String genre, Author author) {
        this.title = title;
        this.isbn = isbn;
        this.publishedDate = publishedDate;
        this.genre = genre;
        this.author = author;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public LocalDate getPublishedDate() {
        return publishedDate;
    }

    public void setPublishedDate(LocalDate publishedDate) {
        this.publishedDate = publishedDate;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }
}
