package com.example.cqrs.command.aggregate;

/**
 * Represents the lifecycle status of an order.
 *
 * <p>State transitions are enforced by the {@link OrderAggregate}:
 * <pre>
 *   PLACED ──confirm──► CONFIRMED  (terminal, cannot be cancelled)
 *   PLACED ──cancel───► CANCELLED  (terminal)
 * </pre>
 *
 * <p>Note: this enum lives in the command package because it represents the write-model
 * aggregate state. The read-model ({@code OrderSummary}) mirrors these values as a String
 * column to avoid coupling the JPA entity to aggregate internals.
 */
public enum OrderStatus {

    /**
     * The order has been placed but not yet confirmed or cancelled.
     * This is the only state from which further transitions are allowed.
     */
    PLACED,

    /**
     * The order has been confirmed. No further transitions are allowed.
     */
    CONFIRMED,

    /**
     * The order has been cancelled. No further transitions are allowed.
     */
    CANCELLED
}
