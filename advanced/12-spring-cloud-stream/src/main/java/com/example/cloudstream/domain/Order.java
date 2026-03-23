package com.example.cloudstream.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Core domain entity representing a customer order.
 *
 * <p>This is a plain Java object (POJO) — no JPA annotations, no database.
 * The focus of this mini-project is the <em>messaging layer</em> (Spring Cloud Stream),
 * not persistence. Orders are stored in an in-memory {@link java.util.concurrent.ConcurrentHashMap}
 * inside {@link com.example.cloudstream.repository.OrderRepository}.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Created by the REST controller with status {@link OrderStatus#PENDING}.</li>
 *   <li>The {@code orderSupplier} picks it up and publishes an {@code OrderPlacedEvent}.</li>
 *   <li>The {@code orderProcessor} function enriches it → {@link OrderStatus#PROCESSING}.</li>
 *   <li>The {@code notificationConsumer} logs a notification → {@link OrderStatus#NOTIFIED}.</li>
 * </ol>
 */
public class Order {

    /** Unique identifier, generated at creation time. */
    private final UUID id;

    /** ID of the customer who placed the order. */
    private final String customerId;

    /** ID of the product being ordered. */
    private final String productId;

    /** Number of units ordered. Must be positive. */
    private final int quantity;

    /** Total price for the order (quantity × unit price). Must be positive. */
    private final BigDecimal totalPrice;

    /** Current lifecycle status of the order. Mutable as the pipeline advances. */
    private OrderStatus status;

    /**
     * Human-readable reason when the order is rejected (null otherwise).
     * Set by the processor when validation fails.
     */
    private String rejectionReason;

    /** Timestamp when this order was first created. */
    private final Instant createdAt;

    /** Timestamp of the most recent status change. Updated on every transition. */
    private Instant updatedAt;

    /**
     * Creates a new order in {@link OrderStatus#PENDING} state.
     *
     * @param customerId ID of the customer
     * @param productId  ID of the product
     * @param quantity   number of units
     * @param totalPrice total cost
     */
    public Order(String customerId, String productId, int quantity, BigDecimal totalPrice) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    // -------------------------------------------------------------------------
    // State transition methods
    //
    // Using explicit methods (rather than setStatus) enforces valid transitions
    // and makes the domain logic readable in service code.
    // -------------------------------------------------------------------------

    /**
     * Advances the order to {@link OrderStatus#PROCESSING}.
     * Called when the orderProcessor Function has consumed the placed event.
     */
    public void markProcessing() {
        this.status = OrderStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    /**
     * Advances the order to {@link OrderStatus#NOTIFIED}.
     * Called when the notificationConsumer has logged the notification.
     */
    public void markNotified() {
        this.status = OrderStatus.NOTIFIED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks the order as {@link OrderStatus#REJECTED} with a reason.
     * Called by the processor when the order fails validation.
     *
     * @param reason human-readable explanation of why the order was rejected
     */
    public void markRejected(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters — no setters; state transitions go through explicit methods above
    // -------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customerId='" + customerId + "', productId='" + productId
                + "', quantity=" + quantity + ", totalPrice=" + totalPrice
                + ", status=" + status + "}";
    }
}
