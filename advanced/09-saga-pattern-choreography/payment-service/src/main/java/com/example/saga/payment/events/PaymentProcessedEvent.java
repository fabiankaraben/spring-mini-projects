package com.example.saga.payment.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound event published by the Payment Service to topic {@code payment.processed}
 * after successfully charging the customer.
 *
 * <p>The Payment Service echoes {@code productId} and {@code quantity} from the
 * original {@link OrderCreatedEvent} so that the Inventory Service receives everything
 * it needs to perform the stock reservation in a single message — without having to
 * query the Order Service directly (which would violate service isolation).
 *
 * @param orderId       the order this payment relates to
 * @param customerId    the customer who was charged
 * @param paymentId     the unique transaction identifier assigned by the Payment Service
 * @param amountCharged the exact amount debited from the customer's account
 * @param productId     echoed from the original order — the product to reserve
 * @param quantity      echoed from the original order — the number of units to reserve
 */
public record PaymentProcessedEvent(
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amountCharged,
        String productId,
        int quantity
) {}
