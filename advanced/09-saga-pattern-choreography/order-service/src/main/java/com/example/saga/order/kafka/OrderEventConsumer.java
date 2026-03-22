package com.example.saga.order.kafka;

import com.example.saga.order.events.*;
import com.example.saga.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the Order Service.
 *
 * <p>This component listens to Kafka topics that carry events published by
 * downstream services (Payment and Inventory) and delegates to {@link OrderService}
 * for all business logic. The consumer acts purely as a transport adapter.
 *
 * <p>Consumer group: {@code order-service-group}
 * Each service uses its own consumer group so every service gets its own copy
 * of every message (i.e., Kafka delivers the same message to each group independently).
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Listens for {@link PaymentProcessedEvent} messages on the {@code payment.processed} topic.
     *
     * <p>Triggered when the Payment Service successfully charges the customer.
     * Delegates to {@link OrderService#handlePaymentProcessed(PaymentProcessedEvent)}.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "payment.processed",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Consumed PaymentProcessedEvent from Kafka: orderId={}", event.orderId());
        orderService.handlePaymentProcessed(event);
    }

    /**
     * Listens for {@link PaymentFailedEvent} messages on the {@code payment.failed} topic.
     *
     * <p>Triggered when the Payment Service fails to charge the customer.
     * Delegates to {@link OrderService#handlePaymentFailed(PaymentFailedEvent)}.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Consumed PaymentFailedEvent from Kafka: orderId={}", event.orderId());
        orderService.handlePaymentFailed(event);
    }

    /**
     * Listens for {@link InventoryReservedEvent} messages on the {@code inventory.reserved} topic.
     *
     * <p>Triggered when the Inventory Service successfully reserves stock.
     * This is the final happy-path event in the saga.
     * Delegates to {@link OrderService#handleInventoryReserved(InventoryReservedEvent)}.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info("Consumed InventoryReservedEvent from Kafka: orderId={}", event.orderId());
        orderService.handleInventoryReserved(event);
    }

    /**
     * Listens for {@link InventoryFailedEvent} messages on the {@code inventory.failed} topic.
     *
     * <p>Triggered when the Inventory Service cannot reserve the requested stock.
     * Delegates to {@link OrderService#handleInventoryFailed(InventoryFailedEvent)}
     * which will trigger a compensation refund.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "inventory.failed",
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(InventoryFailedEvent event) {
        log.info("Consumed InventoryFailedEvent from Kafka: orderId={}", event.orderId());
        orderService.handleInventoryFailed(event);
    }
}
