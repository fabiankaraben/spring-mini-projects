package com.example.saga.order.events;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Domain event published by the Order Service when a new order is created.
 *
 * <p>This event is the <em>starting gun</em> of the saga. It is serialized as JSON
 * and published to the Kafka topic {@code order.created}. The Payment Service
 * subscribes to this topic and will attempt to process the payment.
 *
 * <p>Why a plain record? Java records are ideal for events because:
 * <ul>
 *   <li>Events are immutable by nature — they represent facts that happened.</li>
 *   <li>Records generate {@code equals}, {@code hashCode}, and {@code toString}
 *       automatically, which is useful in tests.</li>
 *   <li>Jackson can deserialize records via the canonical constructor.</li>
 * </ul>
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
