package com.example.debeziumcdc.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing a product in the product catalogue.
 *
 * <p>Every INSERT, UPDATE, or DELETE on the {@code products} table will be
 * captured by Debezium's PostgreSQL connector via the WAL (Write-Ahead Log)
 * and streamed to Kafka as a {@link com.example.debeziumcdc.cdc.ProductCdcEvent}.
 *
 * <p>The table name is explicitly set to "products" to match the Debezium
 * connector's {@code table.include.list} configuration.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id} — auto-generated primary key (BIGSERIAL in PostgreSQL).</li>
 *   <li>{@code name} — product name; must not be blank.</li>
 *   <li>{@code description} — optional product description.</li>
 *   <li>{@code price} — unit price; must be positive.</li>
 *   <li>{@code stock} — available quantity; must be non-negative.</li>
 *   <li>{@code createdAt} — set once on first persist.</li>
 *   <li>{@code updatedAt} — updated on every merge.</li>
 * </ul>
 */
@Entity
@Table(name = "products")
public class Product {

    /**
     * Auto-generated primary key.
     * PostgreSQL uses BIGSERIAL (sequence-backed) for GenerationType.SEQUENCE,
     * which is more efficient than IDENTITY for batch inserts.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "products_id_seq", allocationSize = 1)
    private Long id;

    /**
     * Product name. Must not be blank.
     * Mapped to a NOT NULL column in the database.
     */
    @NotBlank(message = "Product name must not be blank")
    @Column(nullable = false)
    private String name;

    /**
     * Optional human-readable product description.
     * Stored as TEXT to handle long descriptions.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Unit price. Must be positive.
     * Uses NUMERIC(19,2) for precise monetary arithmetic — never use DOUBLE
     * for money values due to floating-point rounding errors.
     */
    @Positive(message = "Price must be positive")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /**
     * Number of units in stock. Must be non-negative.
     * Defaults to 0 if not provided.
     */
    @Column(nullable = false)
    private int stock = 0;

    /**
     * Timestamp when this product was first created.
     * Set once in {@link #prePersist()} and never updated afterwards.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of the last update.
     * Refreshed on every save via {@link #preUpdate()}.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // -------------------------------------------------------------------------
    // JPA lifecycle callbacks
    // -------------------------------------------------------------------------

    /**
     * Called by JPA before the first INSERT.
     * Initialises both {@code createdAt} and {@code updatedAt} to now.
     */
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Called by JPA before every UPDATE.
     * Refreshes {@code updatedAt} to the current time.
     */
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Default no-arg constructor required by JPA. */
    protected Product() {
    }

    /**
     * Convenience constructor for creating a new product.
     *
     * @param name        product display name
     * @param description optional description
     * @param price       unit price (must be positive)
     * @param stock       initial stock level
     */
    public Product(String name, String description, BigDecimal price, int stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    // -------------------------------------------------------------------------
    // Getters and setters
    // -------------------------------------------------------------------------

    public Long getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', price=" + price + ", stock=" + stock + "}";
    }
}
