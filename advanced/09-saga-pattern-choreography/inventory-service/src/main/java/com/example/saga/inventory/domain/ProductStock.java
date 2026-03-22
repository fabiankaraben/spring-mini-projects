package com.example.saga.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity representing the current stock level for a product.
 *
 * <p>This is the core inventory record. When a reservation is requested,
 * the {@code availableQuantity} is decremented atomically (within a
 * {@code @Transactional} boundary) to prevent overselling.
 *
 * <p>Pre-seeding strategy:
 *   Each product ID is inserted on first access (via
 *   {@link com.example.saga.inventory.service.InventoryService#getOrCreateStock(String)})
 *   with {@link #INITIAL_STOCK} units. This makes the demo self-contained —
 *   no manual data setup is required.
 */
@Entity
@Table(name = "product_stock")
public class ProductStock {

    /**
     * Default initial stock level for every product.
     * Products are auto-seeded with this quantity on first encounter.
     */
    public static final int INITIAL_STOCK = 10;

    /**
     * The product identifier used as the natural primary key.
     * Matches the {@code productId} field in saga events.
     */
    @Id
    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * Current number of units available for reservation.
     * Decremented on each successful reservation.
     */
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA no-arg constructor required by Hibernate. */
    protected ProductStock() {}

    /**
     * Creates a new stock record for a product with the default initial quantity.
     *
     * @param productId the product identifier
     */
    public ProductStock(String productId) {
        this.productId = productId;
        this.availableQuantity = INITIAL_STOCK;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Attempts to reserve the requested quantity.
     *
     * @param quantity the number of units to reserve
     * @return {@code true} if sufficient stock was available and reserved;
     *         {@code false} if {@code quantity > availableQuantity}
     */
    public boolean reserve(int quantity) {
        if (quantity > availableQuantity) {
            // Not enough stock — reservation fails
            return false;
        }
        availableQuantity -= quantity;
        updatedAt = Instant.now();
        return true;
    }

    /**
     * Returns the reserved units to stock (compensation / rollback).
     *
     * @param quantity the number of units to return
     */
    public void release(int quantity) {
        availableQuantity += quantity;
        updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getProductId() { return productId; }

    public int getAvailableQuantity() { return availableQuantity; }

    public Instant getUpdatedAt() { return updatedAt; }
}
