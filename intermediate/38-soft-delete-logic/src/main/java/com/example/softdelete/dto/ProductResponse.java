package com.example.softdelete.dto;

import com.example.softdelete.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object returned by the REST API for product responses.
 *
 * <p>This DTO intentionally exposes the soft-delete audit fields ({@code deletedAt})
 * only in the admin/deleted-list context.  For normal responses the {@code deletedAt}
 * field will be {@code null} since only active products are returned by the normal
 * query path.</p>
 *
 * <p>Using a Java {@code record} keeps the DTO immutable and avoids boilerplate
 * getters/setters.</p>
 *
 * @param id          surrogate primary key
 * @param name        product name
 * @param description optional description
 * @param price       unit price
 * @param category    optional category
 * @param deleted     whether the product has been soft-deleted
 * @param deletedAt   timestamp of the soft deletion, or {@code null} if still active
 * @param createdAt   timestamp of creation
 * @param updatedAt   timestamp of last non-deletion update
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean deleted,
        LocalDateTime deletedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Factory method that converts a {@link Product} entity to a {@link ProductResponse}.
     *
     * <p>Centralising the mapping here means controllers and tests do not need to
     * know about the entity's internal structure.</p>
     *
     * @param product the entity to convert
     * @return a new {@link ProductResponse} populated from the entity's fields
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isDeleted(),
                product.getDeletedAt(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
