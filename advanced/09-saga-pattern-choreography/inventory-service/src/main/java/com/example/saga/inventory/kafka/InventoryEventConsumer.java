package com.example.saga.inventory.kafka;

import com.example.saga.inventory.events.PaymentProcessedEvent;
import com.example.saga.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the Inventory Service.
 *
 * <p>Subscribes to topics that trigger inventory actions and delegates
 * all business logic to {@link InventoryService}. This is a pure transport
 * adapter — no business logic lives here.
 *
 * <p>Consumer group: {@code inventory-service-group}
 */
@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryService inventoryService;

    public InventoryEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Listens for {@link PaymentProcessedEvent} on topic {@code payment.processed}.
     *
     * <p>Triggered after the Payment Service successfully processes a charge.
     * Delegates to {@link InventoryService#reserveInventory(PaymentProcessedEvent)}.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "payment.processed",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Consumed PaymentProcessedEvent: orderId={}, productId={}",
                event.orderId(), event.productId());
        inventoryService.reserveInventory(event);
    }
}
