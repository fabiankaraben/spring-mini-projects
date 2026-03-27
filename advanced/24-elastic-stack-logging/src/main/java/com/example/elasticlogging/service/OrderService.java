package com.example.elasticlogging.service;

import com.example.elasticlogging.dto.CreateOrderRequest;
import com.example.elasticlogging.exception.OrderNotFoundException;
import com.example.elasticlogging.model.Order;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service layer for managing {@link Order} entities.
 *
 * <h2>Structured logging patterns demonstrated here</h2>
 *
 * <h3>1. StructuredArguments.kv()</h3>
 * <pre>
 *   log.info("Order created", kv("orderId", order.getId()), kv("customerId", ...));
 * </pre>
 * Each {@code kv()} call adds a first-class JSON field to the log event:
 * <pre>
 *   { "message": "Order created", "orderId": "abc-123", "customerId": "c-001" }
 * </pre>
 * This is better than embedding the values in the message string because Kibana
 * can filter, aggregate, and visualise the fields independently.
 *
 * <h3>2. MDC (Mapped Diagnostic Context)</h3>
 * <pre>
 *   try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
 *       log.info("Processing order ...");
 *   }
 * </pre>
 * MDC entries are automatically included in every JSON log event emitted while
 * the MDC is active. This correlates all log lines from one HTTP request.
 *
 * <h3>3. Log levels as severity signals</h3>
 * <ul>
 *   <li>INFO  — normal business events (order created, status changed)</li>
 *   <li>WARN  — expected but notable failures (order not found, invalid input)</li>
 *   <li>ERROR — unexpected failures (would be retried or alerted on)</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * In-memory order store (thread-safe).
     * In a real application this would be a database repository.
     * Using ConcurrentHashMap so that concurrent REST requests don't corrupt state.
     */
    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    /**
     * Creates a new order and logs the event as a structured JSON line.
     *
     * <p>The log event will contain:
     * <pre>
     *   {
     *     "level":      "INFO",
     *     "message":    "Order created",
     *     "orderId":    "...",
     *     "customerId": "...",
     *     "amount":     "99.99",
     *     "status":     "PENDING"
     *   }
     * </pre>
     *
     * @param request DTO with customer ID, description, and amount
     * @return the newly created order
     */
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.getCustomerId(),
                request.getDescription(),
                request.getAmount()
        );

        orderStore.put(order.getId(), order);

        // StructuredArguments.kv() adds key/value pairs as first-class JSON fields.
        // They appear alongside "message" in the JSON log object so that Kibana can
        // filter by e.g. orderId without parsing the message string.
        log.info("Order created",
                StructuredArguments.kv("orderId", order.getId()),
                StructuredArguments.kv("customerId", order.getCustomerId()),
                StructuredArguments.kv("amount", order.getAmount().toPlainString()),
                StructuredArguments.kv("status", order.getStatus().name())
        );

        return order;
    }

    /**
     * Retrieves an order by its ID.
     *
     * <p>Logs a WARN if the order is not found — this is an expected failure
     * (user-supplied bad ID) rather than a system error.
     *
     * @param orderId the UUID of the order to retrieve
     * @return the found order
     * @throws OrderNotFoundException if no order with this ID exists
     */
    public Order getOrder(String orderId) {
        Order order = orderStore.get(orderId);

        if (order == null) {
            // WARN level: expected failure — the caller supplied a non-existent ID.
            // Including orderId as a structured field lets Kibana count 404s per order.
            log.warn("Order not found",
                    StructuredArguments.kv("orderId", orderId)
            );
            throw new OrderNotFoundException(orderId);
        }

        log.debug("Order retrieved",
                StructuredArguments.kv("orderId", order.getId()),
                StructuredArguments.kv("status", order.getStatus().name())
        );

        return order;
    }

    /**
     * Returns all orders currently in the store (unmodifiable view).
     *
     * @return collection of all orders
     */
    public Collection<Order> getAllOrders() {
        log.debug("Returning all orders",
                StructuredArguments.kv("count", orderStore.size())
        );
        return Collections.unmodifiableCollection(orderStore.values());
    }

    /**
     * Updates the status of an existing order.
     *
     * <p>Demonstrates MDC usage: the previous and new status values are added
     * as structured fields so Kibana can build a state-transition histogram.
     *
     * @param orderId   the UUID of the order to update
     * @param newStatus the desired new status
     * @return the updated order
     * @throws OrderNotFoundException if no order with this ID exists
     */
    public Order updateOrderStatus(String orderId, Order.Status newStatus) {
        Order order = getOrder(orderId); // throws OrderNotFoundException if missing

        Order.Status previousStatus = order.getStatus();
        order.updateStatus(newStatus);

        // Log the status transition with both old and new values as structured fields.
        // This enables Kibana to visualise the state machine transitions over time.
        log.info("Order status updated",
                StructuredArguments.kv("orderId", orderId),
                StructuredArguments.kv("previousStatus", previousStatus.name()),
                StructuredArguments.kv("newStatus", newStatus.name())
        );

        return order;
    }

    /**
     * Simulates an order processing failure and logs it as an ERROR event.
     *
     * <p>This method exists purely to demonstrate ERROR-level structured logging.
     * In Kibana you can create an alert that fires whenever the error rate
     * exceeds a threshold.
     *
     * @param orderId the UUID of the order that failed to process
     * @throws OrderNotFoundException if no order with this ID exists
     */
    public void simulateProcessingFailure(String orderId) {
        Order order = getOrder(orderId); // validates the order exists

        // Simulate a downstream system error (e.g., payment gateway timeout)
        RuntimeException cause = new RuntimeException("Payment gateway timeout");

        // ERROR level: unexpected system failure — this would typically trigger an alert.
        // The "errorType" field lets Kibana distinguish failure categories.
        log.error("Order processing failed",
                StructuredArguments.kv("orderId", orderId),
                StructuredArguments.kv("customerId", order.getCustomerId()),
                StructuredArguments.kv("errorType", "PAYMENT_GATEWAY_TIMEOUT"),
                cause
        );

        // Use MDC to attach the orderId to all subsequent log events in this thread
        // until the MDC entry is cleared (useful for longer request processing chains).
        try (MDC.MDCCloseable ignored = MDC.putCloseable("failedOrderId", orderId)) {
            log.warn("Order marked for retry",
                    StructuredArguments.kv("orderId", orderId),
                    StructuredArguments.kv("retryEligible", true)
            );
        }
    }

    /**
     * Returns the current number of orders in the store.
     * Used by tests to verify store state without iterating all orders.
     *
     * @return count of stored orders
     */
    public int getOrderCount() {
        return orderStore.size();
    }
}
