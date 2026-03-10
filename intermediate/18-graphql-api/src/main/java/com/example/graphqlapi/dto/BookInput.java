package com.example.graphqlapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Input DTO used for creating or updating a {@link com.example.graphqlapi.domain.Book}.
 *
 * <p>Mirrors the {@code BookInput} input type in the GraphQL schema. Spring for
 * GraphQL deserialises the GraphQL input argument into this class automatically.
 *
 * <p>Keeping a separate input DTO rather than reusing the JPA entity directly:
 * <ul>
 *   <li>Prevents unintended mass-assignment of JPA-managed fields (e.g. {@code id}).</li>
 *   <li>Allows the API contract to evolve independently from the database schema.</li>
 *   <li>Enables per-field validation annotations that make sense only at the API boundary.</li>
 * </ul>
 */
public class BookInput {

    /**
     * Title of the book. Required – cannot be blank.
     */
    @NotBlank(message = "Book title must not be blank")
    private String title;

    /**
     * ISBN (International Standard Book Number). Required and must be unique.
     * The service layer enforces uniqueness via the database unique constraint.
     */
    @NotBlank(message = "ISBN must not be blank")
    private String isbn;

    /**
     * Date of first publication. Optional.
     * GraphQL scalar type {@code String} is mapped to {@link LocalDate}
     * by the custom scalar registered in the configuration.
     */
    private LocalDate publishedDate;

    /**
     * Genre / category of the book (e.g. "Science Fiction", "History"). Optional.
     */
    private String genre;

    /**
     * ID of the author who wrote this book. Required – every book must have an author.
     */
    @NotNull(message = "Author ID must not be null")
    private Long authorId;

    /** No-arg constructor required for Spring's data-binding mechanism. */
    public BookInput() {
    }

    public BookInput(String title, String isbn, LocalDate publishedDate, String genre, Long authorId) {
        this.title = title;
        this.isbn = isbn;
        this.publishedDate = publishedDate;
        this.genre = genre;
        this.authorId = authorId;
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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }
}
