package com.example.cassandraintegration.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * DTO for partially updating an existing Product.
 *
 * <p>All fields are optional (nullable). Only non-null fields will be applied
 * to the existing entity in the service layer — this pattern is sometimes
 * called a "partial update" or "PATCH-style" update.</p>
 *
 * <p>Note: In Cassandra, updates are actually upserts at the CQL level.
 * Spring Data Cassandra's {@code save()} method performs a full row replace,
 * so the service merges this DTO with the existing entity before saving.</p>
 */
public class UpdateProductRequest {

    /** New name for the product. If null, the existing name is kept. */
    private String name;

    /** New description for the product. If null, the existing description is kept. */
    private String description;

    /**
     * New price. If null, the existing price is kept.
     * Must be >= 0 when provided.
     */
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    /**
     * New stock quantity. If null, the existing stock is kept.
     * Must be >= 0 when provided.
     */
    @Min(value = 0, message = "Stock must be non-negative")
    private Integer stock;

    /** Required no-arg constructor for Jackson deserialization. */
    public UpdateProductRequest() {}

    /**
     * Full constructor for programmatic use (e.g. in tests).
     */
    public UpdateProductRequest(String name, String description, BigDecimal price, Integer stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }
}
