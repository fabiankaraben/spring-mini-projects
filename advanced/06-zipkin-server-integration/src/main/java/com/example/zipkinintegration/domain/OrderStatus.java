package com.example.zipkinintegration.domain;

/**
 * Lifecycle states an {@link Order} can be in.
 *
 * <p>The state transitions are:
 * <pre>
 *   PENDING → CONFIRMED → SHIPPED → DELIVERED
 *                ↓
 *            CANCELLED
 * </pre>
 *
 * <p>Having a dedicated status enum (rather than a plain String) makes the
 * domain model explicit and prevents invalid state values from being created.
 */
public enum OrderStatus {

    /** Order has been placed but not yet confirmed by inventory. */
    PENDING,

    /** Inventory confirmed that stock is available; order accepted. */
    CONFIRMED,

    /** Order has left the warehouse. */
    SHIPPED,

    /** Order successfully delivered to the customer. */
    DELIVERED,

    /** Order was cancelled before shipment. */
    CANCELLED
}
