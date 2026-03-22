package com.example.resourceserver.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO (Data Transfer Object) for updating an existing product.
 *
 * <p>This class represents the JSON body of a {@code PUT /api/products/{id}} request.
 * All fields are optional — only non-null fields provided by the client will be
 * applied to the existing product (partial update / PATCH-like semantics via PUT).
 *
 * <p><b>Design decision — optional fields in PUT:</b>
 * A strict REST interpretation of PUT would require all fields. However, for an
 * educational demo, allowing partial updates simplifies client usage. In production
 * you would typically separate PATCH (partial update) from PUT (full replacement).
 *
 * <p><b>Example JSON request body (partial update — only name and price):</b>
 * <pre>{@code
 * {
 *   "name": "Updated Headphones Pro",
 *   "price": 179.99
 * }
 * }</pre>
 */
public class UpdateProductRequest {

    /**
     * Updated product name — optional, but must not be blank or exceed 200 characters
     * if provided. A null value means "do not update the name".
     */
    @Size(max = 200, message = "Product name must be at most 200 characters")
    private String name;

    /**
     * Updated description — optional. Null means "do not update the description".
     */
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    /**
     * Updated unit price — optional but must be positive if provided.
     * Null means "do not update the price".
     */
    @Positive(message = "Price must be a positive value")
    private BigDecimal price;

    /**
     * Updated category — optional. Null means "do not update the category".
     */
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    /**
     * Updated stock count — optional but must be zero or positive if provided.
     * Null means "do not update the stock".
     */
    @PositiveOrZero(message = "Stock must be zero or a positive value")
    private Integer stock;

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
