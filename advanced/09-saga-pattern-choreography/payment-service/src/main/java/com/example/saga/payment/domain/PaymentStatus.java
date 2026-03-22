package com.example.saga.payment.domain;

/**
 * Represents the lifecycle state of a {@link Payment} record.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING
 *     → COMPLETED  (payment successfully charged)
 *     → FAILED     (payment rejected — card declined, insufficient funds, etc.)
 *     → REFUNDED   (compensating transaction: payment was charged then reversed)
 * </pre>
 */
public enum PaymentStatus {

    /**
     * Payment record created but not yet processed.
     * Transitional state — immediately transitions to COMPLETED or FAILED.
     */
    PENDING,

    /**
     * Payment was successfully charged.
     * The PaymentProcessedEvent has been published.
     */
    COMPLETED,

    /**
     * Payment attempt was rejected.
     * The PaymentFailedEvent has been published.
     */
    FAILED,

    /**
     * A previously COMPLETED payment was reversed as a saga compensation.
     * Triggered by receiving a PaymentRefundEvent from the Order Service.
     */
    REFUNDED
}
