package com.example.softdelete.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Data Transfer Object for creating or updating a {@link com.example.softdelete.domain.Product}.
 *
 * <p>Using a dedicated DTO instead of exposing the JPA entity directly in the REST API
 * follows best practices:</p>
 * <ul>
 *   <li>It decouples the API contract from the persistence model.</li>
 *   <li>It prevents accidental exposure or modification of internal fields like
 *       {@code deleted} or {@code deletedAt}.</li>
 *   <li>It allows validation annotations to be applied independently from the entity.</li>
 * </ul>
 *
 * <p>Validation is triggered by {@code @Valid} on the controller method parameter.</p>
 */
public record ProductRequest(

        /**
         * Human-readable product name.  Required, 2–100 characters.
         */
        @NotBlank(message = "Product name must not be blank")
        @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
        String name,

        /**
         * Optional free-text description of the product.
         */
        String description,

        /**
         * Unit price.  Required and must be greater than zero.
         */
        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        /**
         * Optional category label (e.g. "Electronics", "Clothing").
         */
        String category
) {}
