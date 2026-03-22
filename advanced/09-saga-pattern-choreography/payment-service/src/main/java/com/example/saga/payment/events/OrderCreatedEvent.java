package com.example.saga.payment.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inbound event consumed by the Payment Service from Kafka topic {@code order.created}.
 *
 * <p>Each service defines its own copy of the events it consumes. This avoids
 * inter-service compile-time coupling — changes to the Order Service's event
 * definition do not force a recompile of the Payment Service as long as the
 * JSON field names remain compatible (a key principle of event-driven design).
 *
 * @param orderId    the unique identifier of the newly created order
 * @param customerId the identifier of the ordering customer
 * @param productId  the identifier of the product ordered
 * @param quantity   how many units were ordered
 * @param totalPrice the total amount to charge for payment processing
 */
public record OrderCreatedEvent(
        UUID orderId,
        String customerId,
        String productId,
        int quantity,
        BigDecimal totalPrice
) {}
