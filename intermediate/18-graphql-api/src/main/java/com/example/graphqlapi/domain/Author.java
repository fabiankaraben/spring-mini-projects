package com.example.graphqlapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity representing an author in the library domain.
 *
 * <p>This entity maps to the {@code authors} table in PostgreSQL. It is the
 * "one" side of a one-to-many relationship with {@link Book}: one author can
 * write many books, but each book has a single author (for simplicity).
 *
 * <p>In the GraphQL schema, {@code Author} is a type that can be queried
 * directly or resolved as a nested field of a {@code Book}.
 */
@Entity
@Table(name = "authors")
public class Author {

    /**
     * Primary key – auto-incremented by the PostgreSQL sequence.
     * Using {@code IDENTITY} strategy maps to PostgreSQL's {@code SERIAL} /
     * {@code GENERATED ALWAYS AS IDENTITY} column type.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Full name of the author. Cannot be blank and must be unique
     * across all authors in the database.
     */
    @Column(nullable = false)
    private String name;

    /**
     * A short biography paragraph. Stored as {@code TEXT} in PostgreSQL
     * (unbounded character varying). Nullable because a newly created author
     * may not have a biography yet.
     */
    @Column(columnDefinition = "TEXT")
    private String bio;

    /**
     * The books written by this author.
     *
     * <p>{@code mappedBy = "author"} tells JPA that the {@code author} field
     * on {@link Book} owns the foreign-key column; this side is the inverse.
     * {@code cascade} is omitted intentionally: book lifecycle is managed
     * independently from the author to keep mutations granular.
     */
    @OneToMany(mappedBy = "author")
    private List<Book> books = new ArrayList<>();

    /** Required no-arg constructor for JPA. */
    protected Author() {
    }

    /**
     * Creates a new Author with the given name and bio.
     *
     * @param name the author's full name
     * @param bio  a short biography (may be {@code null})
     */
    public Author(String name, String bio) {
        this.name = name;
        this.bio = bio;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<Book> getBooks() {
        return books;
    }
}
