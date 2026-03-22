package com.example.cqrs.query.handler;

import com.example.cqrs.command.api.OrderCancelledEvent;
import com.example.cqrs.command.api.OrderConfirmedEvent;
import com.example.cqrs.command.api.OrderPlacedEvent;
import com.example.cqrs.query.api.FindAllOrdersQuery;
import com.example.cqrs.query.api.FindOrderByIdQuery;
import com.example.cqrs.query.model.OrderSummary;
import com.example.cqrs.query.model.OrderSummaryRepository;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The {@code OrderProjection} is the bridge between the command side and the query side.
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li><strong>Event handling</strong>: reacts to domain events published on the Axon event bus
 *       and updates the {@link OrderSummary} read model stored in PostgreSQL.</li>
 *   <li><strong>Query handling</strong>: answers queries ({@link FindOrderByIdQuery},
 *       {@link FindAllOrdersQuery}) by reading from the {@link OrderSummaryRepository}.</li>
 * </ol>
 *
 * <h2>Event flow</h2>
 * <pre>
 *   OrderAggregate.apply(OrderPlacedEvent)
 *       → Axon event store persists the event
 *       → Axon event bus delivers it to OrderProjection.on(OrderPlacedEvent)
 *       → OrderProjection creates an OrderSummary row in PostgreSQL
 * </pre>
 *
 * <h2>Eventual consistency</h2>
 * By default, Axon processes events on the same thread as the command handler,
 * so in this in-process setup the read model is updated synchronously (within
 * the same transaction). In a distributed setup with Axon Server, event processing
 * would be asynchronous and eventual consistency would be visible.
 *
 * <h2>Idempotency</h2>
 * If an event is replayed (e.g. after a crash), the handler should be idempotent.
 * For {@code OrderPlacedEvent} we use {@code save()} which is an upsert via JPA.
 */
@Component  // Spring-managed bean so Axon can register it with the event/query buses
public class OrderProjection {

    private static final Logger log = LoggerFactory.getLogger(OrderProjection.class);

    /** JPA repository for reading and writing the read-model table. */
    private final OrderSummaryRepository repository;

    public OrderProjection(OrderSummaryRepository repository) {
        this.repository = repository;
    }

    // =========================================================================
    //  Event Handlers — update the read model when events arrive
    // =========================================================================

    /**
     * Handles {@link OrderPlacedEvent} — creates a new {@link OrderSummary} row.
     *
     * <p>{@code @EventHandler} tells Axon to call this method whenever an
     * {@code OrderPlacedEvent} is published on the event bus (after being persisted
     * in the event store). This is the point where the read model is populated.
     *
     * @param event the event emitted by the {@code OrderAggregate}
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderPlacedEvent event) {
        log.info("Projecting OrderPlacedEvent: orderId={}, product={}, qty={}",
                event.orderId(), event.productId(), event.quantity());

        OrderSummary summary = new OrderSummary(
                event.orderId(),
                event.productId(),
                event.quantity(),
                event.unitPrice(),
                event.occurredOn()
        );
        repository.save(summary);
    }

    /**
     * Handles {@link OrderConfirmedEvent} — updates the read model status to CONFIRMED.
     *
     * <p>We fetch the existing {@link OrderSummary} by ID and call {@code markConfirmed()}.
     * If the record doesn't exist (edge case during replay), we log a warning and skip.
     *
     * @param event the event emitted by the {@code OrderAggregate}
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderConfirmedEvent event) {
        log.info("Projecting OrderConfirmedEvent: orderId={}", event.orderId());

        repository.findById(event.orderId()).ifPresentOrElse(
                summary -> {
                    summary.markConfirmed(event.occurredOn());
                    repository.save(summary);
                },
                () -> log.warn("OrderSummary not found for orderId={} during confirmation", event.orderId())
        );
    }

    /**
     * Handles {@link OrderCancelledEvent} — updates the read model status to CANCELLED.
     *
     * @param event the event emitted by the {@code OrderAggregate}
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(OrderCancelledEvent event) {
        log.info("Projecting OrderCancelledEvent: orderId={}, reason={}", event.orderId(), event.reason());

        repository.findById(event.orderId()).ifPresentOrElse(
                summary -> {
                    summary.markCancelled(event.occurredOn());
                    repository.save(summary);
                },
                () -> log.warn("OrderSummary not found for orderId={} during cancellation", event.orderId())
        );
    }

    // =========================================================================
    //  Query Handlers — answer queries from the read model
    // =========================================================================

    /**
     * Handles {@link FindOrderByIdQuery} — looks up a single order by ID.
     *
     * <p>{@code @QueryHandler} tells Axon to route {@link FindOrderByIdQuery} messages
     * to this method. The return type ({@code Optional<OrderSummary>}) is propagated
     * back to the caller via the {@code QueryGateway}.
     *
     * @param query the query carrying the order ID
     * @return an {@link Optional} containing the {@link OrderSummary} if found
     */
    @QueryHandler
    public Optional<OrderSummary> handle(FindOrderByIdQuery query) {
        log.debug("Handling FindOrderByIdQuery: orderId={}", query.orderId());
        return repository.findById(query.orderId());
    }

    /**
     * Handles {@link FindAllOrdersQuery} — returns all orders, optionally filtered by status.
     *
     * @param query the query with an optional status filter
     * @return list of matching {@link OrderSummary} objects
     */
    @QueryHandler
    public List<OrderSummary> handle(FindAllOrdersQuery query) {
        log.debug("Handling FindAllOrdersQuery: status={}", query.status());

        if (query.status() != null && !query.status().isBlank()) {
            // Filter by status — delegates to a Spring Data derived query
            return repository.findByStatusOrderByPlacedAtDesc(query.status().toUpperCase());
        }
        // No filter — return all orders
        return repository.findAll();
    }
}
