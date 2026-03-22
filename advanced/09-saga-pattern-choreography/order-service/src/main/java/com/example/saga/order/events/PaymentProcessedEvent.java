package com.example.saga.order.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event published by the Payment Service after successfully charging
 * the customer for an order.
 *
 * <p>The Order Service and the Inventory Service both consume this event:
 * <ul>
 *   <li>Order Service: transitions the order from PAYMENT_PROCESSING to INVENTORY_RESERVING.</li>
 *   <li>Inventory Service: starts the stock reservation process.</li>
 * </ul>
 *
 * <p>Published on Kafka topic: {@code payment.processed}.
 *
 * @param orderId       the order this payment relates to
 * @param customerId    the customer who was charged
 * @param paymentId     the unique transaction identifier assigned by the Payment Service
 * @param amountCharged the exact amount debited from the customer's account
 */
public record PaymentProcessedEvent(
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amountCharged
) {}
