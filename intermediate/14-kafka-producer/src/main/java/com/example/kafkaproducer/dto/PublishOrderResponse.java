package com.example.kafkaproducer.dto;

import com.example.kafkaproducer.domain.OrderStatus;

import java.time.Instant;

/**
 * Response DTO returned by the {@code POST /api/orders} endpoint after an
 * order event is successfully published to Kafka.
 *
 * <p>This record includes enough information for the API caller to:
 * <ul>
 *   <li>Correlate the response with the original request via {@code orderId}.</li>
 *   <li>Track the event through downstream systems using {@code eventId}.</li>
 *   <li>Know which Kafka topic and partition received the message.</li>
 *   <li>Know the exact offset assigned to the message within the partition.</li>
 * </ul>
 *
 * @param eventId   unique identifier auto-generated for this event
 * @param orderId   business identifier of the order (echoed from the request)
 * @param status    order status that was published
 * @param topic     Kafka topic the event was sent to
 * @param partition Kafka partition that received the event
 * @param offset    offset of the message within the partition
 * @param timestamp UTC instant when the event was published
 */
public record PublishOrderResponse(
        String eventId,
        String orderId,
        OrderStatus status,
        String topic,
        int partition,
        long offset,
        Instant timestamp
) {}
