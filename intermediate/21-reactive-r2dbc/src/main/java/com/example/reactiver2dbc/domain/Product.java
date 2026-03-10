package com.example.reactiver2dbc.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity representing a product stored in the PostgreSQL {@code products} table.
 *
 * <p>R2DBC annotation notes:
 * <ul>
 *   <li>{@link Table} maps this class to the {@code products} SQL table. Spring Data R2DBC
 *       uses this to build reactive SQL queries ({@code SELECT}, {@code INSERT}, etc.).</li>
 *   <li>{@link Id} marks the primary key column. R2DBC detects a {@code null} id as a new
 *       entity and issues an {@code INSERT}; a non-null id triggers an {@code UPDATE}.</li>
 *   <li>{@link Column} explicitly maps a Java field to a SQL column name. Useful when the
 *       Java naming convention (camelCase) differs from the SQL convention (snake_case).</li>
 *   <li>{@link CreatedDate} / {@link LastModifiedDate} – Spring Data auditing annotations.
 *       They require enabling auditing via {@code @EnableR2dbcAuditing} (see
 *       {@link com.example.reactiver2dbc.config.R2dbcConfig}).</li>
 * </ul>
 *
 * <p>Why no {@code @Entity} or {@code @GeneratedValue}?
 * Those annotations belong to JPA (Hibernate). R2DBC is a completely separate specification
 * with its own annotation set. The key difference is that R2DBC is <em>non-blocking</em> —
 * it never uses a JDBC connection or blocks a thread waiting for a SQL result.
 *
 * <p>Why {@link BigDecimal} for price?
 * Floating-point types ({@code double}/{@code float}) are imprecise for monetary values due
 * to binary representation issues (e.g., 0.1 + 0.2 ≠ 0.3). {@link BigDecimal} provides
 * exact decimal arithmetic, which is essential for prices and financial data.
 */
@Table("products")
public class Product {

    /**
     * Auto-generated primary key. Left {@code null} on creation; PostgreSQL assigns a
     * BIGSERIAL value on INSERT. Spring Data R2DBC reads the generated key and populates
     * this field after the insert completes.
     */
    @Id
    private Long id;

    /** Short product name displayed in listings (e.g., "Wireless Keyboard"). */
    private String name;

    /** Longer product description (can be multi-sentence). */
    private String description;

    /**
     * Product price in the application's base currency.
     * Stored as NUMERIC(12, 2) in PostgreSQL for exact decimal precision.
     */
    private BigDecimal price;

    /** Product category for grouping/filtering (e.g., "electronics", "books"). */
    private String category;

    /**
     * Available stock quantity. Must be zero or positive; negative stock is not allowed.
     * Stored as INTEGER in PostgreSQL.
     */
    @Column("stock_quantity")
    private int stockQuantity;

    /**
     * Whether the product is visible/active on the storefront.
     * Inactive products are hidden from the public API but retained in the database
     * for historical records (soft-delete style).
     */
    private boolean active;

    /**
     * Timestamp of when this record was first inserted.
     * Set automatically by Spring Data auditing on the first save.
     * Mapped to the {@code created_at} column (TIMESTAMPTZ in PostgreSQL).
     */
    @Column("created_at")
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp of the most recent update to this record.
     * Updated automatically by Spring Data auditing on every save.
     * Mapped to the {@code updated_at} column (TIMESTAMPTZ in PostgreSQL).
     */
    @Column("updated_at")
    @LastModifiedDate
    private Instant updatedAt;

    /**
     * No-arg constructor required by Spring Data R2DBC for result-set mapping.
     * R2DBC reads rows from PostgreSQL and constructs Java objects using this
     * constructor, then sets each field via reflection or setters.
     */
    public Product() {}

    /**
     * Convenience constructor for creating new products (before they are persisted).
     * The {@code id}, {@code createdAt}, and {@code updatedAt} fields are left as
     * their defaults ({@code null} / {@code 0}) and will be populated by the database
     * and Spring Data auditing respectively.
     *
     * @param name          product display name
     * @param description   detailed product description
     * @param price         product price (exact decimal)
     * @param category      category tag
     * @param stockQuantity available units in stock
     * @param active        whether the product is publicly visible
     */
    public Product(String name, String description, BigDecimal price,
                   String category, int stockQuantity, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
        this.active = active;
    }

    // ── Getters and setters ────────────────────────────────────────────────────────
    // Standard JavaBean accessors. Spring Data R2DBC uses setters for deserialization;
    // Jackson uses them for JSON serialisation/deserialisation.

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

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
