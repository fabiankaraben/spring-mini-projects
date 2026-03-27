package com.example.elasticlogging.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a customer order.
 *
 * <p>This class serves as the core business entity in the application.
 * Its lifecycle events (creation, status changes) are the primary source of
 * structured log events that flow through the Elastic Stack pipeline.
 *
 * <p>When an order is created, updated, or fails, the service layer emits JSON
 * log events containing order details as structured key/value fields — not just
 * embedded in the message string. This allows Kibana to filter and aggregate
 * logs by orderId, status, amount, etc.
 */
public class Order {

    /**
     * Possible states of an order throughout its lifecycle.
     * Status transitions are logged as structured events.
     */
    public enum Status {
        /** Order received but not yet processed. */
        PENDING,
        /** Order is actively being fulfilled. */
        PROCESSING,
        /** Order has been successfully shipped. */
        SHIPPED,
        /** Order was cancelled before fulfilment. */
        CANCELLED
    }

    /** Unique identifier for this order (UUID string). */
    private final String id;

    /** ID of the customer who placed the order. */
    private final String customerId;

    /** Human-readable description of the ordered item(s). */
    private final String description;

    /** Total monetary amount for the order. */
    private final BigDecimal amount;

    /** Current lifecycle status of the order. */
    private Status status;

    /** Timestamp when the order was first created. */
    private final Instant createdAt;

    /** Timestamp of the last status change; null if never updated. */
    private Instant updatedAt;

    /**
     * Creates a new Order in {@link Status#PENDING} state.
     *
     * @param customerId  ID of the customer placing the order
     * @param description description of the ordered item(s)
     * @param amount      total price of the order
     */
    public Order(String customerId, String description, BigDecimal amount) {
        this.id = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.description = description;
        this.amount = amount;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * Updates the order's status and records the change timestamp.
     *
     * @param newStatus the new status to transition to
     */
    public void updateStatus(Status newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters — immutable fields have no setters; status is changed via updateStatus()
    // -------------------------------------------------------------------------

    public String getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
