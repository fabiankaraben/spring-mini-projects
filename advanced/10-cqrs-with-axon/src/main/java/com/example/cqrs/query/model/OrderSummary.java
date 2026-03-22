package com.example.cqrs.query.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The {@code OrderSummary} is the <em>read model</em> (projection) for order queries.
 *
 * <h2>Why a separate read model?</h2>
 * In CQRS, the write model (aggregate) is optimised for validating and applying changes.
 * The read model is optimised for querying — it can be denormalised, pre-computed, or
 * shaped exactly as needed by the UI/API, with no joins and no event replay overhead.
 *
 * <h2>How it is populated</h2>
 * The {@code OrderProjection} listens to domain events on the event bus and updates
 * (or creates) rows in this table. This is the classic "eventual consistency" model:
 * the read store is updated <em>after</em> the event has been written to the event store.
 *
 * <h2>Storage</h2>
 * This is a plain JPA entity stored in the same PostgreSQL database as the event store,
 * but in a separate table ({@code order_summaries}). In a larger system it could live
 * in a completely different store (e.g. Elasticsearch, Redis).
 */
@Entity
@Table(name = "order_summaries")
public class OrderSummary {

    /**
     * Primary key — same as the aggregate ID, so we can do a direct lookup.
     * We don't use a generated ID here because the aggregate identifier is already
     * a UUID assigned by the client (client-assigned identity pattern).
     */
    @Id
    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    /**
     * Identifier of the product being ordered.
     */
    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * Number of units ordered.
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Price per unit at the time the order was placed.
     * Stored as DECIMAL(10,2) via JPA defaults.
     */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Current status stored as a plain string so the read model does not depend
     * on the aggregate's OrderStatus enum. Possible values: PLACED, CONFIRMED, CANCELLED.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Timestamp when the order was placed (from the OrderPlacedEvent).
     */
    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    /**
     * Timestamp of the last status update (confirmed or cancelled).
     * Null until the order transitions out of PLACED status.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * No-arg constructor required by JPA.
     */
    protected OrderSummary() {
        // JPA requires a no-arg constructor
    }

    /**
     * Creates a new {@code OrderSummary} when an {@code OrderPlacedEvent} is received.
     *
     * @param orderId   aggregate / order identifier
     * @param productId product being ordered
     * @param quantity  units ordered
     * @param unitPrice price per unit
     * @param placedAt  timestamp from the event
     */
    public OrderSummary(String orderId, String productId, int quantity,
                        BigDecimal unitPrice, Instant placedAt) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = "PLACED";
        this.placedAt = placedAt;
    }

    // =========================================================================
    //  State mutation methods (called by OrderProjection)
    // =========================================================================

    /**
     * Updates the status to CONFIRMED and records the update timestamp.
     *
     * @param updatedAt timestamp from the {@code OrderConfirmedEvent}
     */
    public void markConfirmed(Instant updatedAt) {
        this.status = "CONFIRMED";
        this.updatedAt = updatedAt;
    }

    /**
     * Updates the status to CANCELLED and records the update timestamp.
     *
     * @param updatedAt timestamp from the {@code OrderCancelledEvent}
     */
    public void markCancelled(Instant updatedAt) {
        this.status = "CANCELLED";
        this.updatedAt = updatedAt;
    }

    // =========================================================================
    //  Getters
    // =========================================================================

    public String getOrderId() {
        return orderId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getStatus() {
        return status;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
