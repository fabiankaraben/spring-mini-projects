package com.example.kafkaconsumer.service;

import com.example.kafkaconsumer.domain.OrderEvent;
import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.domain.ProcessedOrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service responsible for processing {@link OrderEvent} objects consumed from Kafka
 * and maintaining an in-memory store of processed events.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Receive a raw {@link OrderEvent} plus its Kafka partition/offset metadata.</li>
 *   <li>Apply domain logic – in this demo we simply log and store; a real system
 *       might trigger fulfilment workflows, update a database, or emit notifications.</li>
 *   <li>Wrap the event into a {@link ProcessedOrderEvent} (adding consumer-side
 *       metadata) and store it for later retrieval via the REST API.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>Spring Kafka can run multiple concurrent consumer threads when
 * {@code concurrency} is greater than 1 on {@code @KafkaListener}. The list of
 * processed events is therefore backed by a {@link CopyOnWriteArrayList}, which
 * is safe to read and write from multiple threads without external synchronisation.
 * For high-throughput production systems, a database-backed store would be more
 * appropriate.
 *
 * <h2>Separation of concerns</h2>
 * <p>Keeping business logic in this service class rather than directly in the
 * {@code @KafkaListener} method makes both the listener and this service independently
 * unit-testable: the listener can be tested with a mock service, and this service
 * can be tested without any Kafka infrastructure.
 */
@Service
public class OrderEventProcessorService implements OrderEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProcessorService.class);

    /**
     * Thread-safe list that accumulates all successfully processed events.
     *
     * <p>{@link CopyOnWriteArrayList} provides thread safety by making an implicit
     * fresh copy of the underlying array on every write operation. This is ideal
     * for use cases where reads are far more frequent than writes, which is
     * typical for event-processing status endpoints.
     */
    private final List<ProcessedOrderEvent> processedEvents = new CopyOnWriteArrayList<>();

    /**
     * Processes a single {@link OrderEvent} received from the Kafka topic.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Log the incoming event at DEBUG level for observability.</li>
     *   <li>Apply status-specific business logic (simulated here with log messages).</li>
     *   <li>Wrap the event into a {@link ProcessedOrderEvent} that includes the
     *       Kafka partition, offset, and processing timestamp.</li>
     *   <li>Store the processed event in the in-memory list.</li>
     * </ol>
     *
     * @param event     the order event deserialised from the Kafka message value
     * @param partition the Kafka partition the message was consumed from
     * @param offset    the offset of the message within the partition
     */
    public void process(OrderEvent event, int partition, long offset) {
        log.debug("Processing order event [eventId={}, orderId={}, status={}, partition={}, offset={}]",
                event.eventId(), event.orderId(), event.status(), partition, offset);

        // Apply status-specific domain logic.
        // In a real application each branch would trigger a real workflow step.
        applyBusinessLogic(event);

        // Wrap the raw event with consumer-side metadata and store it
        ProcessedOrderEvent processed = ProcessedOrderEvent.from(event, partition, offset);
        processedEvents.add(processed);

        log.info("Order event processed and stored [eventId={}, orderId={}, status={}, partition={}, offset={}]",
                processed.eventId(), processed.orderId(), processed.status(),
                processed.partition(), processed.offset());
    }

    /**
     * Returns an unmodifiable view of all events processed so far.
     *
     * <p>The returned list is ordered by insertion (i.e. by Kafka consumption order).
     * Because multiple partitions may be consumed concurrently the order may not
     * perfectly reflect the original production order across partitions.
     *
     * @return an unmodifiable snapshot of all processed events
     */
    public List<ProcessedOrderEvent> getProcessedEvents() {
        // Collections.unmodifiableList prevents callers from mutating the internal list
        return Collections.unmodifiableList(processedEvents);
    }

    /**
     * Returns the total number of events that have been processed.
     *
     * @return count of processed events
     */
    public int getProcessedCount() {
        return processedEvents.size();
    }

    /**
     * Applies status-specific business logic to the incoming event.
     *
     * <p>This method demonstrates how a real consumer application would branch
     * on the event status to trigger different downstream workflows. In this
     * demo the logic is intentionally lightweight (logging only) to keep the
     * focus on Kafka concepts rather than domain complexity.
     *
     * @param event the event to apply logic to
     */
    private void applyBusinessLogic(OrderEvent event) {
        // Route the event to the appropriate handler based on its status.
        // Each case represents a distinct step in the order lifecycle.
        switch (event.status()) {
            case CREATED ->
                // A new order arrived – in production: validate inventory, reserve stock
                log.info("New order received [orderId={}, product={}, quantity={}, totalAmount={}]",
                        event.orderId(), event.product(), event.quantity(), event.totalAmount());

            case CONFIRMED ->
                // Order confirmed – in production: trigger warehouse pick-and-pack workflow
                log.info("Order confirmed – triggering fulfilment [orderId={}]", event.orderId());

            case SHIPPED ->
                // Order shipped – in production: send shipping notification to customer
                log.info("Order shipped – sending customer notification [orderId={}, customerId={}]",
                        event.orderId(), event.customerId());

            case DELIVERED ->
                // Order delivered – in production: close the order, request review
                log.info("Order delivered – requesting customer review [orderId={}, customerId={}]",
                        event.orderId(), event.customerId());

            case CANCELLED ->
                // Order cancelled – in production: release reserved stock, issue refund
                log.info("Order cancelled – releasing stock and issuing refund [orderId={}]",
                        event.orderId());

            default ->
                // Guard against future enum values that this version doesn't handle
                log.warn("Unknown order status [orderId={}, status={}] – no action taken",
                        event.orderId(), event.status());
        }
    }

    /**
     * Returns all processed events that match the given {@link OrderStatus}.
     *
     * <p>This utility method is used by the REST controller to support
     * status-based filtering of the events list.
     *
     * @param status the status to filter by
     * @return an unmodifiable list of processed events with the given status
     */
    public List<ProcessedOrderEvent> getProcessedEventsByStatus(OrderStatus status) {
        return processedEvents.stream()
                .filter(e -> e.status() == status)
                .toList();  // Java 16+ Stream.toList() returns an unmodifiable list
    }
}
