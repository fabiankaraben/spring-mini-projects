package com.example.saga.order.domain;

/**
 * Represents the lifecycle state of an {@link Order} within the saga.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING
 *     → PAYMENT_PROCESSING  (OrderCreatedEvent published, awaiting Payment Service)
 *     → INVENTORY_RESERVING (PaymentProcessedEvent received, awaiting Inventory Service)
 *     → COMPLETED           (InventoryReservedEvent received — saga succeeded)
 *     → CANCELLED           (PaymentFailedEvent or InventoryFailedEvent received — saga rolled back)
 * </pre>
 */
public enum OrderStatus {

    /**
     * Order has been received and saved but the saga has not started yet.
     * This is a transient state that immediately transitions to PAYMENT_PROCESSING.
     */
    PENDING,

    /**
     * OrderCreatedEvent has been published. Waiting for the Payment Service to respond.
     */
    PAYMENT_PROCESSING,

    /**
     * PaymentProcessedEvent received. Waiting for the Inventory Service to confirm stock reservation.
     */
    INVENTORY_RESERVING,

    /**
     * All saga steps completed successfully.
     * Payment was charged and inventory was reserved.
     */
    COMPLETED,

    /**
     * The saga was rolled back due to a failure in one of the participating services.
     * Any already-completed steps were compensated (e.g., payment refunded).
     */
    CANCELLED
}
