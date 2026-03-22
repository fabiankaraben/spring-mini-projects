package com.example.saga.payment.kafka;

import com.example.saga.payment.events.OrderCreatedEvent;
import com.example.saga.payment.events.PaymentRefundEvent;
import com.example.saga.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the Payment Service.
 *
 * <p>Subscribes to topics that trigger payment actions and delegates
 * all business logic to {@link PaymentService}. This component is a
 * pure transport adapter — no business logic lives here.
 *
 * <p>Consumer group: {@code payment-service-group}
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;

    public PaymentEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Listens for {@link OrderCreatedEvent} on topic {@code order.created}.
     *
     * <p>Triggered when a new order is created by the Order Service.
     * Delegates to {@link PaymentService#processPayment(OrderCreatedEvent)}.
     *
     * @param event the deserialized event from Kafka
     */
    @KafkaListener(
            topics = "order.created",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Consumed OrderCreatedEvent: orderId={}, amount={}",
                event.orderId(), event.totalPrice());
        paymentService.processPayment(event);
    }

    /**
     * Listens for {@link PaymentRefundEvent} on topic {@code payment.refund}.
     *
     * <p>Triggered by the Order Service when inventory reservation fails
     * after payment has already been charged. Delegates to
     * {@link PaymentService#processRefund(PaymentRefundEvent)}.
     *
     * @param event the refund instruction from the Order Service
     */
    @KafkaListener(
            topics = "payment.refund",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentRefund(PaymentRefundEvent event) {
        log.info("Consumed PaymentRefundEvent: orderId={}, paymentId={}",
                event.orderId(), event.paymentId());
        paymentService.processRefund(event);
    }
}
