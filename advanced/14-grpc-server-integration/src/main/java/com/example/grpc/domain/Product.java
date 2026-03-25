package com.example.grpc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * JPA entity representing a product in the catalog.
 *
 * <p>This is the persistence layer model. It maps to the {@code products} table in H2
 * and is the canonical source of truth for the domain. The corresponding protobuf message
 * ({@code Product} in product_catalog.proto) is the wire-transfer representation used
 * exclusively on the gRPC boundary — they are deliberately kept separate to allow
 * each layer to evolve independently.
 *
 * <p>Lifecycle transitions:
 * <pre>
 *   ACTIVE ──────── UpdateStock(0) ──► OUT_OF_STOCK
 *   OUT_OF_STOCK ── UpdateStock(>0) ─► ACTIVE (auto-restored in service layer)
 *   ACTIVE / OUT_OF_STOCK ─ DeleteProduct ─► DISCONTINUED (soft-delete)
 * </pre>
 */
@Entity
@Table(name = "products")
public class Product {

    /**
     * Auto-generated primary key. Uses the H2 IDENTITY strategy
     * (auto-increment integer sequence).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable product name (e.g., "Wireless Keyboard").
     * Must not be blank; stored as VARCHAR(255) in H2.
     */
    @NotBlank(message = "Product name must not be blank")
    @Column(nullable = false)
    private String name;

    /**
     * Short description of the product.
     * Stored as TEXT to accommodate longer descriptions.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Product category used for filtering (e.g., "electronics", "furniture").
     * Stored as VARCHAR(100).
     */
    @Column(length = 100)
    private String category;

    /**
     * Unit price in USD. Must be strictly positive (> 0).
     */
    @Positive(message = "Price must be positive")
    @Column(nullable = false)
    private Double price;

    /**
     * Current stock quantity. Must be zero or greater.
     */
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Column(nullable = false)
    private Integer stockQuantity;

    /**
     * Current lifecycle status of the product.
     * Stored as a VARCHAR string (not an integer ordinal) for readability in the DB.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** JPA requires a no-arg constructor. */
    protected Product() {}

    /**
     * Full constructor used by the service layer when creating new products.
     *
     * @param name          product name
     * @param description   product description
     * @param category      product category
     * @param price         unit price (USD)
     * @param stockQuantity initial stock count
     * @param status        initial lifecycle status
     */
    public Product(String name, String description, String category,
                   Double price, Integer stockQuantity, ProductStatus status) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.status = status;
    }

    // -------------------------------------------------------------------------
    // Getters and Setters
    // -------------------------------------------------------------------------

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) { this.status = status; }
}
