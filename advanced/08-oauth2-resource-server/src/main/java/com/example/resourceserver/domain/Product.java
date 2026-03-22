package com.example.resourceserver.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain model representing a Product in the catalog.
 *
 * <p>This is the core domain object for the Products API. It is intentionally
 * kept as a plain Java class (POJO) — no JPA annotations, no database layer.
 * An in-memory store ({@link com.example.resourceserver.repository.ProductRepository})
 * holds these objects to keep the project focused on OAuth2 resource server concerns
 * rather than database setup.
 *
 * <p>In a real microservice this class would typically be a JPA entity or a
 * record backed by a relational or document database.
 *
 * <p><b>Domain fields:</b>
 * <ul>
 *   <li>{@code id}          — unique identifier (auto-incremented long)</li>
 *   <li>{@code name}        — human-readable product name</li>
 *   <li>{@code description} — detailed product description</li>
 *   <li>{@code price}       — unit price (BigDecimal for monetary precision)</li>
 *   <li>{@code category}    — product category (e.g., ELECTRONICS, BOOKS)</li>
 *   <li>{@code stock}       — available inventory count</li>
 *   <li>{@code createdAt}   — creation timestamp (set once on creation)</li>
 *   <li>{@code updatedAt}   — last update timestamp</li>
 * </ul>
 */
public class Product {

    /** Unique numeric identifier for this product. */
    private Long id;

    /** Human-readable product name (e.g., "Wireless Headphones"). */
    private String name;

    /** Full description of the product (features, specifications). */
    private String description;

    /**
     * Unit price in the default currency.
     * Uses {@link BigDecimal} to avoid floating-point rounding errors
     * which are unacceptable for monetary values.
     */
    private BigDecimal price;

    /**
     * Product category string (e.g., "ELECTRONICS", "BOOKS", "CLOTHING").
     * Used for filtering and display grouping.
     */
    private String category;

    /** Number of units currently available in inventory. */
    private Integer stock;

    /** UTC timestamp when this product was first created. */
    private Instant createdAt;

    /** UTC timestamp of the most recent update to this product. */
    private Instant updatedAt;

    /**
     * Default no-arg constructor required for Jackson deserialization.
     */
    public Product() {
    }

    /**
     * Full constructor for creating a product with all fields.
     *
     * @param id          unique identifier
     * @param name        product name
     * @param description product description
     * @param price       unit price
     * @param category    product category
     * @param stock       available stock count
     * @param createdAt   creation timestamp
     * @param updatedAt   last update timestamp
     */
    public Product(Long id, String name, String description,
                   BigDecimal price, String category, Integer stock,
                   Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stock = stock;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', category='" + category
                + "', price=" + price + ", stock=" + stock + "}";
    }
}
