package com.example.optimisticlocking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Immutable DTO used for updating an existing product (PUT requests).
 *
 * <h2>The role of {@code version} in optimistic locking</h2>
 * <p>The {@code version} field is the heart of the optimistic locking contract between
 * client and server:</p>
 * <ol>
 *   <li>A client GETs a product and receives the response including the current
 *       {@code version} value (e.g. {@code 2}).</li>
 *   <li>The client modifies the data locally.</li>
 *   <li>The client PUTs the modified data back, including the same {@code version=2}
 *       value it received in step 1.</li>
 *   <li>The service sets {@code product.setVersion(2)} and calls {@code save()}.
 *       Hibernate generates:
 *       {@code UPDATE products SET ... version=3 WHERE id=? AND version=2}</li>
 *   <li>If another transaction already updated the row (version is now 3), the
 *       WHERE clause matches zero rows → Hibernate throws
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
 *       → HTTP 409 Conflict is returned to the client.</li>
 *   <li>If the version still matches, the update succeeds and the version is
 *       incremented to 3 in the database.</li>
 * </ol>
 *
 * <p>By requiring the client to always supply the version, we guarantee that updates
 * are based on the latest known state and prevent the classic "lost update" problem.</p>
 *
 * @param version     current version of the entity (must match the database value)
 * @param name        new product name (2–100 characters, non-blank)
 * @param description optional new description
 * @param price       new unit price (must be &gt; 0)
 * @param stock       new stock quantity (must be &ge; 0)
 * @param category    optional new category label
 */
public record ProductUpdateRequest(

        @NotNull(message = "Version must not be null – include the current version from the GET response")
        Long version,

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
