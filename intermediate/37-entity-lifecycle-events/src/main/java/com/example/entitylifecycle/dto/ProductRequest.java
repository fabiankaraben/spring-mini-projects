package com.example.entitylifecycle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound DTO (Data Transfer Object) for create and update product requests.
 *
 * <p>Using a dedicated DTO instead of accepting the {@code Product} entity
 * directly in the controller has several benefits:
 * <ul>
 *   <li>Clients cannot accidentally set server-managed fields such as
 *       {@code id}, {@code slug}, {@code createdAt}, {@code updatedAt}, or
 *       {@code discountedPrice} — these are all computed by lifecycle callbacks.</li>
 *   <li>Validation annotations live here rather than on the persistence entity,
 *       keeping concerns separated.</li>
 *   <li>The API shape can evolve independently from the database schema.</li>
 * </ul>
 *
 * <p>Bean Validation constraints are evaluated by Spring MVC when the controller
 * parameter is annotated with {@code @Valid}.
 *
 * @param name            product display name — must not be blank, max 255 chars
 * @param description     full product description
 * @param price           base price — must be positive
 * @param discountPercent discount percentage — must be between 0 and 100 inclusive
 */
public record ProductRequest(

        @NotBlank(message = "Product name must not be blank")
        @Size(max = 255, message = "Product name must not exceed 255 characters")
        String name,

        String description,

        @NotNull(message = "Price must not be null")
        @Positive(message = "Price must be positive")
        BigDecimal price,

        @Min(value = 0, message = "Discount percent must be at least 0")
        @Max(value = 100, message = "Discount percent must be at most 100")
        int discountPercent
) {
}
