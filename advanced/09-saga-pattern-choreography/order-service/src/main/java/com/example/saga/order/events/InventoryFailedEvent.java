package com.example.saga.order.events;

import java.util.UUID;

/**
 * Domain event published by the Inventory Service when stock reservation fails
 * (e.g., insufficient stock for the requested quantity).
 *
 * <p>On receiving this event, the Order Service must execute compensating transactions:
 * <ol>
 *   <li>Publish a {@link PaymentRefundEvent} so the Payment Service refunds the customer.</li>
 *   <li>Mark the order as {@code CANCELLED}.</li>
 * </ol>
 *
 * <p>Published on Kafka topic: {@code inventory.failed}.
 *
 * @param orderId   the order for which inventory reservation could not be completed
 * @param productId the product that had insufficient stock
 * @param reason    human-readable description of why the reservation failed
 *                  (e.g., "Insufficient stock: requested 5, available 2")
 */
public record InventoryFailedEvent(
        UUID orderId,
        String productId,
        String reason
) {}
