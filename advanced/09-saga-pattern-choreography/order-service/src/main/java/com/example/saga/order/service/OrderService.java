package com.example.saga.order.service;

import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderStatus;
import com.example.saga.order.events.*;
import com.example.saga.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core business logic for the Order Service.
 *
 * <p>This class contains the saga orchestration intelligence for the Order Service:
 * <ul>
 *   <li>Creating orders and publishing the initiating event.</li>
 *   <li>Reacting to events from Payment and Inventory services.</li>
 *   <li>Triggering compensating transactions when failures occur.</li>
 * </ul>
 *
 * <p>The {@code @Transactional} annotation on event-handling methods ensures that
 * the database update and the Kafka publish are either both attempted or neither —
 * though note that true atomicity across a database and Kafka requires the Outbox
 * pattern (not implemented here for simplicity).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Kafka topic names — kept as constants to avoid typos and enable refactoring. */
    public static final String TOPIC_ORDER_CREATED  = "order.created";
    public static final String TOPIC_PAYMENT_REFUND = "payment.refund";

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * In-memory store mapping orderId → paymentId received from PaymentProcessedEvent.
     * This is used during compensation: when inventory fails, the Order Service needs
     * the paymentId to publish a PaymentRefundEvent.
     *
     * <p>In production, this would be persisted in the Order table or a separate
     * saga state table.
     */
    private final Map<UUID, UUID> paymentIdByOrderId = new ConcurrentHashMap<>();

    /**
     * In-memory store mapping orderId → amountCharged received from PaymentProcessedEvent.
     * Used during compensation to know the exact refund amount.
     */
    private final Map<UUID, BigDecimal> amountChargedByOrderId = new ConcurrentHashMap<>();

    public OrderService(OrderRepository orderRepository,
                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Creates a new order and starts the saga.
     *
     * <ol>
     *   <li>Persist the order with status {@code PENDING}.</li>
     *   <li>Transition to {@code PAYMENT_PROCESSING}.</li>
     *   <li>Publish {@link OrderCreatedEvent} to Kafka — this triggers the Payment Service.</li>
     * </ol>
     *
     * @param customerId identifier of the customer placing the order
     * @param productId  identifier of the product being ordered
     * @param quantity   number of units requested
     * @param totalPrice total price to be charged
     * @return the persisted {@link Order} entity
     */
    @Transactional
    public Order createOrder(String customerId, String productId, int quantity, BigDecimal totalPrice) {
        // Step 1: persist the order in PENDING state
        Order order = new Order(customerId, productId, quantity, totalPrice);
        order = orderRepository.save(order);
        log.info("Order created: id={}, customer={}, product={}, qty={}, total={}",
                order.getId(), customerId, productId, quantity, totalPrice);

        // Step 2: transition to PAYMENT_PROCESSING (saga has started)
        order.markPaymentProcessing();
        order = orderRepository.save(order);

        // Step 3: publish the saga-initiating event
        OrderCreatedEvent event = new OrderCreatedEvent(
                order.getId(), customerId, productId, quantity, totalPrice);
        kafkaTemplate.send(TOPIC_ORDER_CREATED, order.getId().toString(), event);
        log.info("Published OrderCreatedEvent for orderId={}", order.getId());

        return order;
    }

    /**
     * Retrieves an order by its identifier.
     *
     * @param orderId the UUID of the order
     * @return an {@link Optional} containing the order if found
     */
    @Transactional(readOnly = true)
    public Optional<Order> findById(UUID orderId) {
        return orderRepository.findById(orderId);
    }

    /**
     * Handles a {@link PaymentProcessedEvent} from the Payment Service.
     *
     * <p>Transitions the order from {@code PAYMENT_PROCESSING} to
     * {@code INVENTORY_RESERVING}. The payment details are stored in memory
     * so they can be used for compensation if inventory subsequently fails.
     *
     * @param event the incoming event
     */
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Received PaymentProcessedEvent: orderId={}, paymentId={}",
                event.orderId(), event.paymentId());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            // Guard: only process if we are waiting for payment
            if (order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
                log.warn("Ignoring PaymentProcessedEvent — unexpected status={} for orderId={}",
                        order.getStatus(), event.orderId());
                return;
            }

            // Store payment details for potential compensation
            paymentIdByOrderId.put(event.orderId(), event.paymentId());
            amountChargedByOrderId.put(event.orderId(), event.amountCharged());

            order.markInventoryReserving();
            orderRepository.save(order);
            log.info("Order {} transitioned to INVENTORY_RESERVING", event.orderId());

        }, () -> log.error("Order not found for PaymentProcessedEvent: orderId={}", event.orderId()));
    }

    /**
     * Handles a {@link PaymentFailedEvent} from the Payment Service.
     *
     * <p>Compensating transaction: marks the order as CANCELLED.
     * No refund is needed because payment was never successfully charged.
     *
     * @param event the incoming event
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent: orderId={}, reason={}", event.orderId(), event.reason());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
                log.warn("Ignoring PaymentFailedEvent — unexpected status={} for orderId={}",
                        order.getStatus(), event.orderId());
                return;
            }

            order.markCancelled("Payment failed: " + event.reason());
            orderRepository.save(order);
            log.info("Order {} CANCELLED due to payment failure: {}", event.orderId(), event.reason());

        }, () -> log.error("Order not found for PaymentFailedEvent: orderId={}", event.orderId()));
    }

    /**
     * Handles an {@link InventoryReservedEvent} from the Inventory Service.
     *
     * <p>This is the <em>happy-path terminal event</em>: the saga completed
     * successfully. Marks the order as {@code COMPLETED}.
     *
     * @param event the incoming event
     */
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Received InventoryReservedEvent: orderId={}, reservationId={}",
                event.orderId(), event.reservationId());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.INVENTORY_RESERVING) {
                log.warn("Ignoring InventoryReservedEvent — unexpected status={} for orderId={}",
                        order.getStatus(), event.orderId());
                return;
            }

            order.markCompleted();
            orderRepository.save(order);

            // Clean up the in-memory compensation store
            paymentIdByOrderId.remove(event.orderId());
            amountChargedByOrderId.remove(event.orderId());

            log.info("Order {} COMPLETED successfully", event.orderId());

        }, () -> log.error("Order not found for InventoryReservedEvent: orderId={}", event.orderId()));
    }

    /**
     * Handles an {@link InventoryFailedEvent} from the Inventory Service.
     *
     * <p>Compensating transactions:
     * <ol>
     *   <li>Publish {@link PaymentRefundEvent} to trigger the Payment Service to refund the charge.</li>
     *   <li>Mark the order as {@code CANCELLED}.</li>
     * </ol>
     *
     * @param event the incoming event
     */
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event) {
        log.info("Received InventoryFailedEvent: orderId={}, reason={}", event.orderId(), event.reason());

        orderRepository.findById(event.orderId()).ifPresentOrElse(order -> {
            if (order.getStatus() != OrderStatus.INVENTORY_RESERVING) {
                log.warn("Ignoring InventoryFailedEvent — unexpected status={} for orderId={}",
                        order.getStatus(), event.orderId());
                return;
            }

            // Retrieve stored payment context for compensation
            UUID paymentId = paymentIdByOrderId.get(event.orderId());
            BigDecimal amount = amountChargedByOrderId.get(event.orderId());

            if (paymentId != null && amount != null) {
                // Publish compensating event: ask Payment Service to refund
                PaymentRefundEvent refundEvent = new PaymentRefundEvent(
                        event.orderId(),
                        order.getCustomerId(),
                        paymentId,
                        amount,
                        "Inventory reservation failed: " + event.reason()
                );
                kafkaTemplate.send(TOPIC_PAYMENT_REFUND, event.orderId().toString(), refundEvent);
                log.info("Published PaymentRefundEvent for orderId={}, paymentId={}",
                        event.orderId(), paymentId);
            } else {
                log.error("Cannot publish refund — no payment context stored for orderId={}", event.orderId());
            }

            order.markCancelled("Inventory failed: " + event.reason());
            orderRepository.save(order);

            // Clean up
            paymentIdByOrderId.remove(event.orderId());
            amountChargedByOrderId.remove(event.orderId());

            log.info("Order {} CANCELLED due to inventory failure: {}", event.orderId(), event.reason());

        }, () -> log.error("Order not found for InventoryFailedEvent: orderId={}", event.orderId()));
    }

    /**
     * Returns a snapshot of all saga state data stored in memory.
     * Useful for diagnostics/testing.
     *
     * @return map with keys "paymentIds" and "amountsCharged"
     */
    public Map<String, Object> getSagaStateSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("paymentIds", new HashMap<>(paymentIdByOrderId));
        snapshot.put("amountsCharged", new HashMap<>(amountChargedByOrderId));
        return snapshot;
    }
}
