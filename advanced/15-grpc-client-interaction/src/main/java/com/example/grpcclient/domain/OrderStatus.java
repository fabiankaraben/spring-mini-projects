package com.example.grpcclient.domain;

/**
 * Lifecycle status of an order in the system.
 *
 * <p>This Java enum mirrors the {@code OrderStatus} enum defined in
 * {@code order_service.proto}. The two are kept separate so the JPA entity
 * and the gRPC wire format can evolve independently. The mapping between them
 * is handled in {@link com.example.grpcclient.mapper.OrderMapper}.
 *
 * <p>Status transitions:
 * <pre>
 *   PENDING ─── confirm (inventory reserved) ──► CONFIRMED
 *   CONFIRMED ─ ship ──────────────────────────► SHIPPED
 *   SHIPPED ─── deliver ───────────────────────► DELIVERED
 *   PENDING / CONFIRMED ─ cancel ──────────────► CANCELLED
 * </pre>
 */
public enum OrderStatus {

    /**
     * Order has been placed and is waiting to be confirmed (inventory not yet reserved).
     */
    PENDING,

    /**
     * Order confirmed — inventory has been successfully reserved.
     */
    CONFIRMED,

    /**
     * Order has been shipped to the customer.
     */
    SHIPPED,

    /**
     * Order has been delivered to the customer.
     */
    DELIVERED,

    /**
     * Order was cancelled before shipment (inventory reservation released).
     */
    CANCELLED
}
