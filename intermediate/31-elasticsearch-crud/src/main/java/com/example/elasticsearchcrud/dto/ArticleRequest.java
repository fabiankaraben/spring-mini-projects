package com.example.elasticsearchcrud.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object (DTO) for creating or updating an {@link com.example.elasticsearchcrud.domain.Article}.
 *
 * <p>This record is what clients send in the HTTP request body (JSON). It is separate
 * from the domain entity for several reasons:
 * <ul>
 *   <li>The domain entity carries Elasticsearch mapping annotations; the DTO
 *       carries Bean Validation annotations — keeping concerns separated.</li>
 *   <li>Clients cannot set internal fields like {@code id}, {@code createdAt},
 *       or {@code updatedAt} directly, which prevents data tampering.</li>
 *   <li>Bean Validation constraints here are enforced by Spring MVC via
 *       {@code @Valid} on the controller method parameter, returning HTTP 400
 *       automatically when any constraint is violated.</li>
 * </ul>
 *
 * <p>We use a Java {@code record} for immutability and conciseness: the compiler
 * generates the constructor, accessors, {@code equals}, {@code hashCode}, and
 * {@code toString} automatically.
 *
 * @param title     article title (must not be blank, max 255 characters)
 * @param content   article body text (must not be blank)
 * @param author    author's full name (must not be blank)
 * @param category  category tag (must not be blank, e.g. "technology")
 * @param viewCount initial view count; must be zero or positive
 */
public record ArticleRequest(

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Content must not be blank")
        String content,

        @NotBlank(message = "Author must not be blank")
        String author,

        @NotBlank(message = "Category must not be blank")
        String category,

        @Min(value = 0, message = "View count must be zero or positive")
        int viewCount
) {
}
