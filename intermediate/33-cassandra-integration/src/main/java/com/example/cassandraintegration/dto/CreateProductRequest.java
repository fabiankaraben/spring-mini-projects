package com.example.cassandraintegration.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO (Data Transfer Object) for creating a new Product.
 *
 * <p>Decouples the HTTP request payload from the domain entity.
 * Bean Validation annotations ({@code @NotBlank}, {@code @NotNull}, etc.)
 * ensure the request is validated before it reaches the service layer.</p>
 */
public class CreateProductRequest {

    /**
     * The product category (maps to the Cassandra partition key).
     * Must not be blank — Cassandra requires the partition key for every insert.
     */
    @NotBlank(message = "Category must not be blank")
    private String category;

    /**
     * Human-readable product name.
     * Must not be blank.
     */
    @NotBlank(message = "Name must not be blank")
    private String name;

    /** Optional description of the product. */
    private String description;

    /**
     * Product price. Must not be null and must be >= 0.
     */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.0", message = "Price must be non-negative")
    private BigDecimal price;

    /**
     * Stock quantity. Must be >= 0.
     */
    @Min(value = 0, message = "Stock must be non-negative")
    private int stock;

    /** Required no-arg constructor for Jackson deserialization. */
    public CreateProductRequest() {}

    /**
     * Full constructor for programmatic use (e.g. in tests).
     */
    public CreateProductRequest(String category, String name, String description,
                                BigDecimal price, int stock) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

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

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
