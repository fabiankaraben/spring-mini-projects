package com.example.saga.inventory.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity recording a stock reservation made for a specific order.
 *
 * <p>Each successful inventory reservation creates one {@link Reservation} record.
 * This provides an audit trail and enables idempotency checks — if a
 * PaymentProcessedEvent is received twice for the same order, the second
 * processing is skipped because a reservation already exists.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The saga order this reservation was made for.
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * The product whose stock was decremented.
     */
    @Column(name = "product_id", nullable = false)
    private String productId;

    /**
     * Number of units reserved.
     */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    /**
     * Whether the reservation was successful ({@code true}) or failed ({@code false}).
     */
    @Column(name = "successful", nullable = false)
    private boolean successful;

    /**
     * Reason for failure, populated only when {@code successful = false}.
     */
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA no-arg constructor required by Hibernate. */
    protected Reservation() {}

    /**
     * Creates a new reservation record.
     *
     * @param orderId       the saga order
     * @param productId     the product reserved
     * @param quantity      units reserved
     * @param successful    whether the reservation succeeded
     * @param failureReason reason for failure (null if successful)
     */
    public Reservation(UUID orderId, String productId, int quantity,
                       boolean successful, String failureReason) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.successful = successful;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public UUID getOrderId() { return orderId; }

    public String getProductId() { return productId; }

    public int getQuantity() { return quantity; }

    public boolean isSuccessful() { return successful; }

    public String getFailureReason() { return failureReason; }

    public Instant getCreatedAt() { return createdAt; }
}
