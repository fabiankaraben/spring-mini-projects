package com.example.saga.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a Payment record in the Payment Service.
 *
 * <p>Each payment tracks:
 * <ul>
 *   <li>The saga order it belongs to ({@code orderId}).</li>
 *   <li>The customer who was charged ({@code customerId}).</li>
 *   <li>The amount charged or refunded.</li>
 *   <li>The current {@link PaymentStatus} within the saga lifecycle.</li>
 * </ul>
 *
 * <p>The Payment Service owns this table entirely — the Order Service never
 * reads from it directly. Data is exchanged only via Kafka events.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The saga order this payment was made for.
     * Used to correlate with events from the Order Service.
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * Identifier of the customer whose account was charged.
     */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * The amount charged to the customer.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Current status of this payment in the saga lifecycle.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * Human-readable reason when status is FAILED or REFUNDED.
     */
    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA no-arg constructor required by Hibernate. */
    protected Payment() {}

    /**
     * Creates a new Payment in PENDING status.
     *
     * @param orderId    the associated saga order
     * @param customerId the customer being charged
     * @param amount     the charge amount
     */
    public Payment(UUID orderId, String customerId, BigDecimal amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Domain behaviour
    // -------------------------------------------------------------------------

    /**
     * Marks this payment as successfully completed (charge applied).
     */
    public void markCompleted() {
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks this payment as failed with a reason.
     *
     * @param reason why the payment was rejected
     */
    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Marks this payment as refunded (compensation transaction applied).
     *
     * @param reason why the refund was issued
     */
    public void markRefunded(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }

    public UUID getOrderId() { return orderId; }

    public String getCustomerId() { return customerId; }

    public BigDecimal getAmount() { return amount; }

    public PaymentStatus getStatus() { return status; }

    public String getReason() { return reason; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
