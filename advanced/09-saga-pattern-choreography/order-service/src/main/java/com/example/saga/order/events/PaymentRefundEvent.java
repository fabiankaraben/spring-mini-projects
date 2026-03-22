package com.example.saga.order.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compensating event published by the Order Service when inventory reservation fails
 * after a payment has already been successfully processed.
 *
 * <p>This event triggers the Payment Service to refund the customer, completing
 * the rollback (compensation) portion of the saga.
 *
 * <p>Published on Kafka topic: {@code payment.refund}.
 *
 * <p>Why does Order Service publish this (not Inventory Service)?
 *   In choreography, each service is responsible for its own compensations.
 *   The Order Service knows the full context (payment ID, amount charged) because
 *   it received and stored the {@link PaymentProcessedEvent} earlier in the saga.
 *
 * @param orderId       the order being cancelled
 * @param customerId    the customer to refund
 * @param paymentId     the original payment transaction to reverse
 * @param amountToRefund the exact amount to refund
 * @param reason        why the refund is being issued
 */
public record PaymentRefundEvent(
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amountToRefund,
        String reason
) {}
