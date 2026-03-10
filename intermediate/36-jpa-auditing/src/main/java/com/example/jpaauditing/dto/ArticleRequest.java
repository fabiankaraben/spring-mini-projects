package com.example.jpaauditing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO (Data Transfer Object) for create and update article requests.
 *
 * <p>Using a dedicated DTO instead of accepting the {@code Article} entity
 * directly in the controller has several benefits:
 * <ul>
 *   <li>Clients cannot accidentally set fields managed by the server (e.g.
 *       {@code id}, {@code createdAt}, {@code updatedAt}).</li>
 *   <li>Validation annotations live here rather than on the persistence entity,
 *       keeping concerns separated.</li>
 *   <li>The API shape can evolve independently from the database schema.</li>
 * </ul>
 *
 * <p>Bean Validation constraints are evaluated by Spring MVC when the controller
 * parameter is annotated with {@code @Valid}.
 *
 * @param title   article title — must not be blank, max 255 chars
 * @param content article body — must not be blank
 * @param author  author name  — must not be blank, max 100 chars
 */
public record ArticleRequest(

        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @NotBlank(message = "Content must not be blank")
        String content,

        @NotBlank(message = "Author must not be blank")
        @Size(max = 100, message = "Author name must not exceed 100 characters")
        String author
) {
}
