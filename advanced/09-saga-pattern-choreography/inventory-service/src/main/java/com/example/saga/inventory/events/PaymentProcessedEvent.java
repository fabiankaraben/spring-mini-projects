package com.example.saga.inventory.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound event consumed by the Inventory Service from topic {@code payment.processed}.
 *
 * <p>Each service owns its own copy of the events it consumes to avoid
 * compile-time coupling between services.
 *
 * <p>The Inventory Service's copy adds {@code productId} and {@code quantity}
 * because those fields are needed to perform the stock reservation. The Payment
 * Service publishes these fields as part of its enriched event payload (it
 * echoes them from the original OrderCreatedEvent it consumed).
 *
 * @param orderId       the order to reserve inventory for
 * @param customerId    the customer who placed the order
 * @param paymentId     the payment transaction that was successfully processed
 * @param amountCharged the amount that was charged (for reference/logging)
 * @param productId     the product whose stock must be reserved
 * @param quantity      the number of units to reserve
 */
public record PaymentProcessedEvent(
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amountCharged,
        String productId,
        int quantity
) {}
