package com.example.dockercomposesupport.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Incoming request payload for creating or updating a book.
 *
 * <p>Using a dedicated DTO (Data Transfer Object) keeps the domain entity
 * ({@link com.example.dockercomposesupport.domain.Book}) decoupled from the API
 * contract. Bean Validation annotations are applied here so that invalid
 * requests are rejected before reaching the service layer.</p>
 *
 * @param title           book title — required, must not be blank
 * @param author          author name — required, must not be blank
 * @param isbn            ISBN identifier — required, must not be blank
 * @param publicationYear optional publication year — must be positive if provided
 * @param description     optional short description or summary
 */
public record BookRequest(

        @NotBlank(message = "Title must not be blank")
        String title,

        @NotBlank(message = "Author must not be blank")
        String author,

        @NotBlank(message = "ISBN must not be blank")
        String isbn,

        @Min(value = 1, message = "Publication year must be a positive number")
        Integer publicationYear,

        String description
) {}
