package com.example.cqrs.command.aggregate;

import com.example.cqrs.command.api.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The {@code OrderAggregate} is the core domain object on the <em>command side</em> of CQRS.
 *
 * <h2>What is an Aggregate?</h2>
 * An aggregate is a cluster of domain objects treated as a single unit for data changes.
 * It is the guardian of business invariants — before any state change occurs, the aggregate
 * validates that the change is allowed by the business rules.
 *
 * <h2>Event Sourcing</h2>
 * Instead of persisting the current state directly, the aggregate emits <em>events</em>
 * (e.g. {@code OrderPlacedEvent}) via {@link AggregateLifecycle#apply}. Axon stores these
 * events in the event store. When the aggregate needs to be loaded (e.g. to handle a new
 * command), Axon replays all its past events through the {@code @EventSourcingHandler} methods
 * to reconstruct the current state — no snapshot of the object is ever stored.
 *
 * <h2>Command handling flow</h2>
 * <pre>
 *   1. REST controller sends a command to CommandGateway
 *   2. Axon routes the command to the matching @CommandHandler in this class
 *   3. The command handler validates business rules using the current aggregate state
 *   4. On success: AggregateLifecycle.apply(new SomeEvent(...)) is called
 *   5. Axon persists the event in the event store
 *   6. Axon calls the matching @EventSourcingHandler to update in-memory state
 *   7. Axon publishes the event on the event bus (projection handlers receive it)
 * </pre>
 *
 * <h2>State transitions</h2>
 * <pre>
 *   (none) ──PlaceOrderCommand──► PLACED
 *   PLACED ──ConfirmOrderCommand─► CONFIRMED
 *   PLACED ──CancelOrderCommand──► CANCELLED
 *   CONFIRMED / CANCELLED ──(no further transitions allowed)
 * </pre>
 */
@Aggregate  // Marks this class as an Axon aggregate — Axon will manage its lifecycle
public class OrderAggregate {

    /**
     * The aggregate identifier — used by Axon to route commands and to look up
     * the event stream for this aggregate instance in the event store.
     *
     * The {@code @AggregateIdentifier} annotation is required; Axon uses it to
     * correlate the identifier from the command's {@code @TargetAggregateIdentifier}
     * field with this field when loading or creating the aggregate.
     */
    @AggregateIdentifier
    private String orderId;

    /** Current status of this order; starts as PLACED, transitions on events. */
    private OrderStatus status;

    /** Product being ordered — stored in-memory after replay. */
    private String productId;

    /** Number of units ordered. */
    private int quantity;

    /** Price per unit at the time of order placement. */
    private BigDecimal unitPrice;

    /**
     * No-arg constructor required by Axon.
     *
     * <p>Axon uses reflection to instantiate aggregates before replaying events.
     * This constructor MUST NOT contain any business logic — Axon will call
     * {@code @EventSourcingHandler} methods to populate the fields.
     */
    @SuppressWarnings("unused")
    protected OrderAggregate() {
        // Required by Axon Framework for event sourcing reconstruction
    }

    // =========================================================================
    //  Command Handlers (write side)
    // =========================================================================

    /**
     * Handles the {@link PlaceOrderCommand} — creates a new order aggregate.
     *
     * <p>This is a <em>creation command handler</em>: it is invoked by Axon when there is
     * no existing aggregate with the given ID. It validates input and, if valid, applies
     * an {@link OrderPlacedEvent}.
     *
     * <p>The {@code @CommandHandler} constructor pattern is idiomatic in Axon — it lets
     * the aggregate class both handle the creation command and initialise itself.
     *
     * @param command the incoming place-order command
     */
    @CommandHandler
    public OrderAggregate(PlaceOrderCommand command) {
        // Business rule: quantity must be positive (Belt-and-suspenders; also validated at REST layer)
        if (command.quantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, got: " + command.quantity());
        }
        if (command.unitPrice() == null || command.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }

        // Apply the event — Axon will persist it and call @EventSourcingHandler to update state
        AggregateLifecycle.apply(new OrderPlacedEvent(
                command.orderId(),
                command.productId(),
                command.quantity(),
                command.unitPrice(),
                Instant.now()
        ));
    }

    /**
     * Handles the {@link ConfirmOrderCommand} — confirms a placed order.
     *
     * <p>Business rule: only a {@code PLACED} order can be confirmed.
     *
     * @param command the confirm-order command
     */
    @CommandHandler
    public void handle(ConfirmOrderCommand command) {
        // Business invariant: cannot confirm an already-confirmed or cancelled order
        if (status != OrderStatus.PLACED) {
            throw new IllegalStateException(
                    "Cannot confirm order in status: " + status + " (orderId=" + orderId + ")"
            );
        }

        AggregateLifecycle.apply(new OrderConfirmedEvent(command.orderId(), Instant.now()));
    }

    /**
     * Handles the {@link CancelOrderCommand} — cancels a placed order.
     *
     * <p>Business rule: only a {@code PLACED} order can be cancelled.
     * A confirmed order cannot be cancelled (immutable after confirmation).
     *
     * @param command the cancel-order command
     */
    @CommandHandler
    public void handle(CancelOrderCommand command) {
        // Business invariant: cannot cancel a confirmed or already-cancelled order
        if (status == OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Cannot cancel a confirmed order (orderId=" + orderId + ")"
            );
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Order is already cancelled (orderId=" + orderId + ")"
            );
        }

        AggregateLifecycle.apply(new OrderCancelledEvent(command.orderId(), command.reason(), Instant.now()));
    }

    // =========================================================================
    //  Event Sourcing Handlers (state reconstruction)
    // =========================================================================

    /**
     * Applies the {@link OrderPlacedEvent} to reconstruct aggregate state.
     *
     * <p>This method is called by Axon:
     * <ol>
     *   <li>Immediately after {@code AggregateLifecycle.apply()} during command handling</li>
     *   <li>When loading the aggregate from the event store (replaying history)</li>
     * </ol>
     *
     * <p>Important: these handlers must be pure state assignments — no side effects,
     * no new events, no calls to external systems.
     */
    @EventSourcingHandler
    public void on(OrderPlacedEvent event) {
        this.orderId = event.orderId();
        this.productId = event.productId();
        this.quantity = event.quantity();
        this.unitPrice = event.unitPrice();
        this.status = OrderStatus.PLACED;
    }

    /**
     * Applies the {@link OrderConfirmedEvent} to update aggregate state.
     */
    @EventSourcingHandler
    public void on(OrderConfirmedEvent event) {
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Applies the {@link OrderCancelledEvent} to update aggregate state.
     */
    @EventSourcingHandler
    public void on(OrderCancelledEvent event) {
        this.status = OrderStatus.CANCELLED;
    }

    // =========================================================================
    //  Accessors (for testing / internal use only)
    // =========================================================================

    public String getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
