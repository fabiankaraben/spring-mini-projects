package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Domain logic layer for processing received order messages.
 *
 * <p>This service is called by {@link MessageConsumerService} after a JMS message
 * is received and deserialised. It represents the actual business logic that would
 * run in a real order-processing system (inventory checks, payment, shipping, etc.).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Apply domain rules to the received {@link OrderMessage}.</li>
 *   <li>Keep an in-memory record of processed orders — this is a simple demo;
 *       a real application would persist them to a database.</li>
 *   <li>Expose read access to the processed orders list so the REST API can
 *       serve a summary of what has been consumed.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>{@link MessageConsumerService} runs on a JMS listener thread pool, while
 * the HTTP controller reads the processed orders from a request thread.
 * {@link CopyOnWriteArrayList} is used to make the list safe for concurrent
 * reads (from HTTP) and writes (from the JMS listener thread) without external
 * synchronisation.
 *
 * <h2>Educational note: why keep this separate from the consumer?</h2>
 * <p>Separating the <em>transport concern</em> (JMS listener) from the
 * <em>domain concern</em> (order processing logic) follows the Single Responsibility
 * Principle and makes the domain logic independently testable — no JMS infrastructure
 * is needed to unit-test this service.
 */
@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    /**
     * In-memory store of all orders processed so far.
     *
     * <p>{@link CopyOnWriteArrayList} is thread-safe: writes create a fresh copy of
     * the underlying array, so concurrent reads on the HTTP thread are never blocked.
     * This is ideal for a low-write / high-read scenario like this demo.
     *
     * <p>In a production application this would be replaced by a database repository.
     */
    private final List<OrderMessage> processedOrders = new CopyOnWriteArrayList<>();

    /**
     * Process a single {@link OrderMessage} received from the JMS queue.
     *
     * <p>In a real application this method would:
     * <ol>
     *   <li>Validate the message for business rule compliance.</li>
     *   <li>Perform idempotency check using {@code message.getMessageId()} to
     *       avoid double-processing on re-delivery.</li>
     *   <li>Reserve inventory, trigger payment, create shipment records, etc.</li>
     *   <li>Persist the processed state to a database.</li>
     * </ol>
     *
     * <p>For this demo we simply log the message and append it to the in-memory list.
     *
     * @param message the deserialised order message from the JMS queue
     */
    public void processOrder(OrderMessage message) {
        log.info("Processing order: orderId={}, product={}, quantity={}, messageId={}",
                message.getOrderId(),
                message.getProduct(),
                message.getQuantity(),
                message.getMessageId());

        // In a real system: perform business logic here (inventory, payment, shipping…)
        // For this demo: simply record the processed order in memory
        processedOrders.add(message);

        log.info("Order processed successfully. Total processed so far: {}", processedOrders.size());
    }

    /**
     * Return an unmodifiable snapshot of all orders processed so far.
     *
     * <p>Used by the REST controller to expose a summary of consumed messages.
     * The returned list is a snapshot — modifications to the internal list after
     * this method returns are not reflected in the returned list.
     *
     * @return an unmodifiable view of all processed orders, ordered by processing time
     */
    public List<OrderMessage> getProcessedOrders() {
        // Return an unmodifiable view to prevent callers from mutating the internal state
        return Collections.unmodifiableList(new ArrayList<>(processedOrders));
    }

    /**
     * Return the total count of orders processed since the application started.
     *
     * @return the number of orders that have been successfully processed
     */
    public int getProcessedOrderCount() {
        return processedOrders.size();
    }
}
