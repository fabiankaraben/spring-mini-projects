package com.example.optimisticlocking.dto;

import com.example.optimisticlocking.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable DTO used for returning product data in API responses.
 *
 * <h2>Why expose the version field?</h2>
 * <p>The {@code version} field is deliberately included in every response.
 * Clients must echo it back in PUT (update) requests so that the server can detect
 * concurrent modification conflicts.  Omitting the version would break the
 * optimistic locking contract.</p>
 *
 * <p>The static factory method {@link #from(Product)} keeps the mapping logic
 * in one place and avoids leaking the JPA entity across layer boundaries.</p>
 *
 * @param id          the product's primary key
 * @param version     the current optimistic-locking version counter
 * @param name        product name
 * @param description optional description
 * @param price       unit price
 * @param stock       available stock quantity
 * @param category    optional category
 * @param createdAt   timestamp of initial creation
 * @param updatedAt   timestamp of last modification
 */
public record ProductResponse(
        Long id,
        Long version,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Maps a {@link Product} JPA entity to a {@link ProductResponse} DTO.
     *
     * <p>Using a static factory method instead of a constructor call at every
     * usage site ensures all field mappings stay in a single place.</p>
     *
     * @param product the entity to convert (must not be {@code null})
     * @return a new {@link ProductResponse} populated from the entity's fields
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getVersion(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCategory(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
