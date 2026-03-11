package com.example.testcontainerspostgres.dto;

import com.example.testcontainerspostgres.entity.Product;

import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) for outgoing Product responses.
 *
 * <p>This record is what the REST API returns to clients after create, read
 * and update operations. It mirrors the {@link Product} entity fields but
 * lives in the DTO layer, keeping the HTTP API decoupled from the JPA entity.
 *
 * <p>Benefits of a separate response DTO:
 * <ul>
 *   <li>We can include computed or formatted fields without polluting the entity.</li>
 *   <li>We can exclude internal/sensitive fields from the HTTP response.</li>
 *   <li>Jackson serialises records natively — no extra configuration needed.</li>
 * </ul>
 *
 * <p>The static factory method {@link #from(Product)} provides a clean way
 * to convert an entity to a DTO, centralising the mapping in one place.
 *
 * @param id            the database-generated product ID
 * @param name          the product name
 * @param description   the product description (may be null)
 * @param price         the unit price
 * @param stockQuantity the current stock quantity
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity
) {

    /**
     * Factory method that maps a {@link Product} entity to a {@link ProductResponse} DTO.
     *
     * <p>Having the mapping logic here (in the DTO) rather than in the service keeps
     * the service lean and makes it easy to find all mapping code in one class.
     *
     * @param product the entity to convert; must not be null
     * @return a new {@code ProductResponse} populated from the entity's fields
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity()
        );
    }
}
