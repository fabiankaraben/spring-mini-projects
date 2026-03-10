package com.example.reactivewebfluxapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for creating and updating articles.
 *
 * <p>A DTO separates the API contract (what the client sends) from the domain
 * model (how data is stored in MongoDB). Benefits:
 * <ul>
 *   <li>Validation annotations live here, not on the entity, keeping the entity clean.</li>
 *   <li>API fields can evolve independently from the storage schema.</li>
 *   <li>Sensitive or computed fields (e.g., {@code id}, {@code createdAt}) are never
 *       accidentally overwritten by untrusted client input.</li>
 * </ul>
 *
 * <p>Bean Validation annotations (Jakarta Validation API) are evaluated by Spring
 * WebFlux when the controller method parameter is annotated with {@code @Valid}.
 * If any constraint fails, Spring returns HTTP 400 Bad Request automatically.
 */
public class ArticleRequest {

    /**
     * Article headline — must be non-blank and at most 200 characters.
     * {@code @NotBlank} rejects {@code null}, empty string, and whitespace-only values.
     */
    @NotBlank(message = "Title must not be blank")
    @Size(max = 200, message = "Title must be 200 characters or fewer")
    private String title;

    /**
     * Full article body — must be non-blank.
     */
    @NotBlank(message = "Content must not be blank")
    private String content;

    /**
     * Author display name — must be non-blank.
     */
    @NotBlank(message = "Author must not be blank")
    private String author;

    /**
     * Category tag — must be non-blank (e.g., "technology", "sports", "science").
     */
    @NotBlank(message = "Category must not be blank")
    private String category;

    /**
     * Publication status — {@code true} means the article is visible to readers.
     * Defaults to {@code false} (draft) if omitted in JSON; Jackson maps missing
     * boolean fields to the primitive default.
     */
    private boolean published;

    /**
     * No-arg constructor for Jackson JSON deserialization.
     * Jackson needs to instantiate the object before setting field values.
     */
    public ArticleRequest() {}

    /**
     * All-args constructor for convenient test setup.
     *
     * @param title     article headline
     * @param content   full body text
     * @param author    author display name
     * @param category  category tag
     * @param published whether the article is publicly visible
     */
    public ArticleRequest(String title, String content, String author, String category, boolean published) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = category;
        this.published = published;
    }

    // ── Getters and setters ────────────────────────────────────────────────────────

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
}
