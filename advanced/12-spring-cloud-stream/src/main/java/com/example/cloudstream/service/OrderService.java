package com.example.cloudstream.service;

import com.example.cloudstream.domain.Order;
import com.example.cloudstream.events.OrderPlacedEvent;
import com.example.cloudstream.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Domain service for order management and the Spring Cloud Stream supplier bridge.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Create new {@link Order} objects and persist them via {@link OrderRepository}.</li>
 *   <li>Enqueue {@link OrderPlacedEvent} messages so the {@code orderSupplier}
 *       ({@link java.util.function.Supplier}) can pick them up and send them to Kafka.</li>
 *   <li>Update order status when downstream events (processed / rejected) arrive.</li>
 * </ol>
 *
 * <p>Bridge between REST and Spring Cloud Stream Supplier:
 * Spring Cloud Stream's {@link java.util.function.Supplier} is polled on a fixed
 * schedule. To trigger a message immediately when a REST request arrives, we use a
 * {@link LinkedBlockingQueue} as a simple in-memory hand-off buffer:
 * <pre>
 *   REST handler → orderService.placeOrder() → pendingEvents.offer(event)
 *   Supplier bean  ← pendingEvents.poll()    ← polled by Spring Cloud Stream scheduler
 * </pre>
 * This is a standard pattern for demand-driven (non-scheduled) Supplier production.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** In-memory queue that bridges REST-triggered events to the Supplier bean. */
    private final BlockingQueue<OrderPlacedEvent> pendingEvents = new LinkedBlockingQueue<>();

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new order, saves it, and enqueues an {@link OrderPlacedEvent}
     * for the Spring Cloud Stream supplier to pick up.
     *
     * @param customerId  customer placing the order
     * @param productId   product being ordered
     * @param quantity    units ordered (must be positive)
     * @param totalPrice  total cost (must be positive)
     * @return the persisted {@link Order}
     */
    public Order placeOrder(String customerId, String productId, int quantity, BigDecimal totalPrice) {
        // Create and persist the domain object
        Order order = new Order(customerId, productId, quantity, totalPrice);
        orderRepository.save(order);
        log.info("Order created: id={}, customer={}, product={}", order.getId(), customerId, productId);

        // Enqueue the event for the Supplier to pick up
        OrderPlacedEvent event = OrderPlacedEvent.from(order);
        pendingEvents.offer(event);
        log.debug("OrderPlacedEvent enqueued for orderId={}", order.getId());

        return order;
    }

    /**
     * Retrieves an order by its ID.
     *
     * @param id the order UUID
     * @return an {@link Optional} containing the order, or empty if not found
     */
    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }

    /**
     * Returns all orders in the in-memory store.
     *
     * @return collection of all orders
     */
    public Collection<Order> findAll() {
        return orderRepository.findAll();
    }

    /**
     * Marks an order as {@code PROCESSING}.
     * Called by the {@code orderProcessor} Function after it publishes
     * the enriched event to the {@code orders-processed} topic.
     *
     * @param orderId the ID of the order to update
     */
    public void markProcessing(UUID orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    order.markProcessing();
                    orderRepository.save(order);
                    log.info("Order {} marked PROCESSING", orderId);
                },
                () -> log.warn("markProcessing: order {} not found", orderId)
        );
    }

    /**
     * Marks an order as {@code NOTIFIED}.
     * Called by the {@code notificationConsumer} Consumer after it logs the notification.
     *
     * @param orderId the ID of the order to update
     */
    public void markNotified(UUID orderId) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    order.markNotified();
                    orderRepository.save(order);
                    log.info("Order {} marked NOTIFIED", orderId);
                },
                () -> log.warn("markNotified: order {} not found", orderId)
        );
    }

    /**
     * Marks an order as {@code REJECTED} with the given reason.
     * Called by the {@code orderProcessor} Function when validation fails.
     *
     * @param orderId the ID of the order to update
     * @param reason  human-readable rejection reason
     */
    public void markRejected(UUID orderId, String reason) {
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    order.markRejected(reason);
                    orderRepository.save(order);
                    log.info("Order {} marked REJECTED: {}", orderId, reason);
                },
                () -> log.warn("markRejected: order {} not found", orderId)
        );
    }

    // -------------------------------------------------------------------------
    // Supplier bridge — used by the Spring Cloud Stream Supplier bean
    // -------------------------------------------------------------------------

    /**
     * Polls the next pending {@link OrderPlacedEvent} from the queue.
     *
     * <p>This method is called by the {@code orderSupplier} Supplier bean on each
     * polling cycle. If the queue is empty, it returns {@code null}, which causes
     * Spring Cloud Stream to skip this polling cycle without sending any message.
     *
     * @return the next event to publish, or {@code null} if the queue is empty
     */
    public OrderPlacedEvent pollNextEvent() {
        return pendingEvents.poll();
    }
}
