package com.example.mongodbcrudapi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Data Transfer Object (DTO) for create and update product requests.
 *
 * <p>A DTO decouples the API contract from the internal domain model ({@link com.example.mongodbcrudapi.domain.Product}).
 * This means the API can evolve independently of the storage schema, and vice versa.
 *
 * <p>Bean Validation annotations enforce input constraints before the request reaches
 * the service layer. Spring Boot's default error handler converts constraint violations
 * into a 400 Bad Request response with detailed field error messages.
 *
 * <p>Validation annotations used:
 * <ul>
 *   <li>{@code @NotBlank} – rejects null, empty, and whitespace-only strings.</li>
 *   <li>{@code @NotNull} – rejects null values for non-String fields.</li>
 *   <li>{@code @DecimalMin} – ensures the price is a positive value.</li>
 *   <li>{@code @Min} – ensures stock quantity is zero or more.</li>
 * </ul>
 */
public class ProductRequest {

    /**
     * Product name (mandatory).
     * {@code @NotBlank} rejects null, empty strings, and strings containing only whitespace.
     */
    @NotBlank(message = "Product name must not be blank")
    private String name;

    /**
     * Short description of the product.
     * {@code @NotBlank} ensures a meaningful description is always provided.
     */
    @NotBlank(message = "Description must not be blank")
    private String description;

    /**
     * Retail price in the default currency.
     * Must be greater than zero (inclusive = false with "0.01" as minimum).
     */
    @NotNull(message = "Price must not be null")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    private BigDecimal price;

    /**
     * Category the product belongs to (e.g. "electronics", "books").
     * {@code @NotBlank} prevents empty category strings.
     */
    @NotBlank(message = "Category must not be blank")
    private String category;

    /**
     * Number of units available in stock.
     * Must be zero or positive; negative stock is not meaningful.
     */
    @NotNull(message = "Stock quantity must not be null")
    @Min(value = 0, message = "Stock quantity must be zero or greater")
    private Integer stockQuantity;

    // ── Constructors ─────────────────────────────────────────────────────────────

    /** No-arg constructor required by Jackson for JSON deserialisation. */
    public ProductRequest() {}

    /**
     * Convenience all-args constructor used in tests.
     *
     * @param name          product name
     * @param description   short description
     * @param price         retail price
     * @param category      product category
     * @param stockQuantity available units in stock
     */
    public ProductRequest(String name, String description, BigDecimal price,
                          String category, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
    }

    // ── Getters and setters ───────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }
}
