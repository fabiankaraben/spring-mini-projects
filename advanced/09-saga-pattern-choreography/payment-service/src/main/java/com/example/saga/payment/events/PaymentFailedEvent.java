package com.example.saga.payment.events;

import java.util.UUID;

/**
 * Outbound event published by the Payment Service to topic {@code payment.failed}
 * when the payment attempt is rejected.
 *
 * @param orderId    the order for which payment failed
 * @param customerId the customer whose payment was rejected
 * @param reason     human-readable reason for the rejection
 */
public record PaymentFailedEvent(
        UUID orderId,
        String customerId,
        String reason
) {}
