package com.example.batchschedulers.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity representing a product in the catalog.
 *
 * <p>Products are the main data domain of this mini-project. The scheduled batch
 * jobs operate on products by:
 * <ul>
 *   <li>Refreshing their {@code price} (priceRefreshJob).</li>
 *   <li>Auditing their {@code stockQuantity} and setting {@code lowStock} flag
 *       (inventoryAuditJob).</li>
 *   <li>Generating summary reports from their data (reportGenerationJob).</li>
 * </ul>
 *
 * <p>The table {@code products} is created by Flyway migration V1.
 */
@Entity
@Table(name = "products")
public class Product {

    /**
     * Auto-generated surrogate primary key.
     * Uses the database SEQUENCE strategy for PostgreSQL compatibility.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "products_id_seq", allocationSize = 1)
    private Long id;

    /**
     * Human-readable product name, e.g. "Laptop Pro 15".
     * Must be unique so we can reference products by name in tests.
     */
    @NotBlank(message = "Product name must not be blank")
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Product category, e.g. "Electronics", "Books", "Clothing".
     * Used by the report generation job to group products.
     */
    @NotBlank(message = "Category must not be blank")
    @Column(nullable = false)
    private String category;

    /**
     * Current unit price of the product.
     * Updated by the price refresh batch job.
     */
    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be positive")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Number of units currently in stock.
     * Used by the inventory audit job to flag low-stock items.
     */
    @PositiveOrZero(message = "Stock quantity must be zero or positive")
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    /**
     * Flag set to {@code true} by the inventory audit job when
     * {@code stockQuantity} falls below {@code LOW_STOCK_THRESHOLD}.
     */
    @Column(name = "low_stock", nullable = false)
    private boolean lowStock = false;

    /**
     * Timestamp of the last price update, set by the price refresh job.
     */
    @Column(name = "last_price_update")
    private LocalDateTime lastPriceUpdate;

    /**
     * Timestamp of the last inventory audit, set by the inventory audit job.
     */
    @Column(name = "last_audited")
    private LocalDateTime lastAudited;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Required by JPA. */
    protected Product() {}

    /**
     * Convenience constructor for creating new products.
     *
     * @param name          product name
     * @param category      product category
     * @param price         unit price
     * @param stockQuantity units in stock
     */
    public Product(String name, String category, BigDecimal price, int stockQuantity) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    // ── Domain logic ──────────────────────────────────────────────────────────

    /**
     * Low-stock threshold: products with fewer than this many units are flagged.
     * Used by the inventory audit job's {@code ItemProcessor}.
     */
    public static final int LOW_STOCK_THRESHOLD = 10;

    /**
     * Applies a price adjustment factor to the current price.
     *
     * <p>For example, a factor of {@code 1.05} raises the price by 5%.
     * This is called by the price-refresh {@code ItemProcessor}.
     *
     * @param factor multiplier (e.g. 0.95 for a 5% discount, 1.10 for 10% markup)
     */
    public void applyPriceAdjustment(BigDecimal factor) {
        // Multiply current price by the factor and round to 2 decimal places
        this.price = this.price.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
        this.lastPriceUpdate = LocalDateTime.now();
    }

    /**
     * Audits the current stock level and sets the {@code lowStock} flag.
     *
     * <p>If {@code stockQuantity} is below {@link #LOW_STOCK_THRESHOLD} the flag
     * is set to {@code true}; otherwise it is cleared. The audit timestamp is
     * always updated. This is called by the inventory-audit {@code ItemProcessor}.
     */
    public void auditInventory() {
        this.lowStock = this.stockQuantity < LOW_STOCK_THRESHOLD;
        this.lastAudited = LocalDateTime.now();
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public boolean isLowStock() { return lowStock; }
    public void setLowStock(boolean lowStock) { this.lowStock = lowStock; }

    public LocalDateTime getLastPriceUpdate() { return lastPriceUpdate; }
    public void setLastPriceUpdate(LocalDateTime lastPriceUpdate) { this.lastPriceUpdate = lastPriceUpdate; }

    public LocalDateTime getLastAudited() { return lastAudited; }
    public void setLastAudited(LocalDateTime lastAudited) { this.lastAudited = lastAudited; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', category='" + category +
               "', price=" + price + ", stockQuantity=" + stockQuantity +
               ", lowStock=" + lowStock + "}";
    }
}
