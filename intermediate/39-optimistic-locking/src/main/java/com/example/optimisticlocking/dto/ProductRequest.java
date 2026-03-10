package com.example.optimisticlocking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Immutable DTO used for creating a new product (POST requests).
 *
 * <p>Java records are used here because they are ideal for simple, immutable data
 * carriers: the compiler generates the constructor, getters, {@code equals},
 * {@code hashCode}, and {@code toString} automatically.</p>
 *
 * <p>Bean Validation annotations on record components are applied automatically
 * to the constructor parameters in Spring Boot 3.x.</p>
 *
 * @param name        product name (2–100 characters, non-blank)
 * @param description optional description
 * @param price       unit price (must be &gt; 0)
 * @param stock       initial stock quantity (must be &ge; 0)
 * @param category    optional category label
 */
public record ProductRequest(

        @NotBlank(message = "Product name must not be blank")
        @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
        String name,

        String description,

        @NotNull(message = "Price must not be null")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        BigDecimal price,

        @NotNull(message = "Stock must not be null")
        Integer stock,

        String category
) {}
