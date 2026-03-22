package com.example.saga.order.events;

import java.util.UUID;

/**
 * Domain event published by the Payment Service when payment processing fails.
 *
 * <p>On receiving this event, the Order Service must execute a compensating
 * transaction: mark the order as CANCELLED and record the reason.
 *
 * <p>Because payment was never successfully charged, no refund is needed —
 * the compensation here is purely local (update the order status).
 *
 * <p>Published on Kafka topic: {@code payment.failed}.
 *
 * @param orderId    the order for which payment could not be processed
 * @param customerId the customer whose payment attempt failed
 * @param reason     human-readable description of why the payment failed
 *                   (e.g., "Insufficient funds", "Card declined")
 */
public record PaymentFailedEvent(
        UUID orderId,
        String customerId,
        String reason
) {}
