package com.example.saga.payment.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound compensating event consumed by the Payment Service from topic {@code payment.refund}.
 *
 * <p>Published by the Order Service when inventory reservation fails after payment
 * has already been charged. The Payment Service processes this to reverse the charge.
 *
 * @param orderId        the order being cancelled
 * @param customerId     the customer to refund
 * @param paymentId      the original payment transaction to reverse
 * @param amountToRefund the exact amount to refund
 * @param reason         why the refund is being issued
 */
public record PaymentRefundEvent(
        UUID orderId,
        String customerId,
        UUID paymentId,
        BigDecimal amountToRefund,
        String reason
) {}
