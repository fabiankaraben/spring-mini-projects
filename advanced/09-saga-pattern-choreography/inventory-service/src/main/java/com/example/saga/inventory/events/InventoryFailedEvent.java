package com.example.saga.inventory.events;

import java.util.UUID;

/**
 * Outbound event published by the Inventory Service to topic {@code inventory.failed}
 * when the requested quantity exceeds available stock.
 *
 * <p>This event triggers the compensation flow in the Order Service,
 * which then publishes a {@code PaymentRefundEvent} to reverse the charge.
 *
 * @param orderId   the order for which reservation failed
 * @param productId the product that had insufficient stock
 * @param reason    human-readable description of the failure
 */
public record InventoryFailedEvent(
        UUID orderId,
        String productId,
        String reason
) {}
