package com.example.multitenancy.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Immutable DTO (Data Transfer Object) for creating and updating a product.
 *
 * <p>Using a Java {@code record} gives us an immutable value type with a compact
 * syntax: the compiler automatically generates the constructor, accessor methods,
 * {@code equals}, {@code hashCode}, and {@code toString}.</p>
 *
 * <p>Bean Validation annotations ({@code @NotBlank}, {@code @Positive}, etc.) are
 * declared here instead of on the {@link com.example.multitenancy.domain.Product}
 * entity. This is intentional: the DTO represents the API contract (what the client
 * sends), while entity-level constraints guard the persistence layer. Separating the
 * two layers is good practice — the API shape may differ from the storage shape.</p>
 *
 * <h2>Validation</h2>
 * <p>The controller uses {@code @Valid} on the request body parameter to trigger
 * validation. If any constraint is violated, Spring returns a 400 Bad Request
 * response with details of the failing constraints.</p>
 *
 * @param name          the product name (required, must not be blank)
 * @param description   an optional product description
 * @param price         the product price (required, must be positive)
 * @param stockQuantity the number of units in stock (must be zero or positive)
 */
public record ProductRequest(

        /**
         * Product name.
         * Required; a blank or missing name is rejected with HTTP 400.
         */
        @NotBlank(message = "Product name must not be blank")
        String name,

        /**
         * Optional human-readable description of the product.
         * May be {@code null} or empty.
         */
        String description,

        /**
         * Product price.
         * Required; must be a strictly positive decimal value.
         * {@code @NotNull} catches a missing price field before {@code @Positive} sees it —
         * {@code @Positive} considers {@code null} valid (it skips null values by design).
         */
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be a positive value")
        BigDecimal price,

        /**
         * Available stock quantity.
         * Must be zero or a positive integer.
         * Zero represents an "out of stock" state.
         */
        @PositiveOrZero(message = "Stock quantity must be zero or positive")
        int stockQuantity
) {}
