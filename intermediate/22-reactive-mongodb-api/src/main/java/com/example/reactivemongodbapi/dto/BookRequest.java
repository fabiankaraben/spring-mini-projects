package com.example.reactivemongodbapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Data Transfer Object (DTO) for creating and updating books.
 *
 * <p>A DTO separates the API contract (what the client sends) from the domain
 * model (how data is stored in MongoDB). Benefits:
 * <ul>
 *   <li>Validation annotations live here, not on the entity, keeping the entity clean.</li>
 *   <li>API fields can evolve independently from the storage schema.</li>
 *   <li>Sensitive or auto-generated fields ({@code id}, {@code createdAt},
 *       {@code updatedAt}) are never accidentally overwritten by client input.</li>
 *   <li>The client cannot set internal state (e.g., they cannot forge timestamps).</li>
 * </ul>
 *
 * <p>Bean Validation annotations (Jakarta Validation API) are evaluated by Spring WebFlux
 * when the controller method parameter is annotated with {@code @Valid}. If any constraint
 * fails, Spring returns HTTP 400 Bad Request automatically before the method is invoked.
 */
public class BookRequest {

    /**
     * Book title — must be non-blank and at most 500 characters.
     * {@code @NotBlank} rejects {@code null}, empty string, and whitespace-only values.
     */
    @NotBlank(message = "Title must not be blank")
    @Size(max = 500, message = "Title must be 500 characters or fewer")
    private String title;

    /**
     * Author's full name — must be non-blank and at most 200 characters.
     */
    @NotBlank(message = "Author must not be blank")
    @Size(max = 200, message = "Author must be 200 characters or fewer")
    private String author;

    /**
     * ISBN — must be non-blank. The ISBN uniquely identifies the book edition.
     * Stored with a unique index in MongoDB.
     */
    @NotBlank(message = "ISBN must not be blank")
    private String isbn;

    /**
     * Book price — must be provided and at least 0.01 (no free or negative prices).
     * Stored as BSON {@code double} for correct numeric range query behaviour.
     */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
    private Double price;

    /**
     * Year of publication — must be at least 1 (year 1 AD is a reasonable floor).
     */
    @Min(value = 1, message = "Published year must be a positive integer")
    private int publishedYear;

    /**
     * List of genres — optional. Can be {@code null} or empty.
     * Stored as a BSON array inside the MongoDB document (no join table required).
     */
    private List<String> genres;

    /**
     * Short synopsis of the book — must be non-blank.
     */
    @NotBlank(message = "Description must not be blank")
    private String description;

    /**
     * Language the book is written in — must be non-blank (e.g., "English").
     */
    @NotBlank(message = "Language must not be blank")
    private String language;

    /**
     * Number of pages — must be at least 1.
     */
    @Min(value = 1, message = "Page count must be at least 1")
    private int pageCount;

    /**
     * Whether the book is available for purchase / display.
     * Defaults to {@code false} (unavailable) if omitted in JSON.
     */
    private boolean available;

    /**
     * No-arg constructor required by Jackson for JSON deserialization.
     */
    public BookRequest() {}

    /**
     * All-args constructor for convenient test setup.
     *
     * @param title         book title
     * @param author        author's full name
     * @param isbn          unique ISBN
     * @param price         book price
     * @param publishedYear year of first publication
     * @param genres        list of genre tags
     * @param description   short synopsis
     * @param language      language of the book
     * @param pageCount     number of pages
     * @param available     whether the book is publicly visible
     */
    public BookRequest(String title, String author, String isbn, Double price,
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
}
