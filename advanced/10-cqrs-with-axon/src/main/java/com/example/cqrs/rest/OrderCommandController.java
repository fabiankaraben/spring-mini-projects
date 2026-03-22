package com.example.cqrs.rest;

import com.example.cqrs.command.api.CancelOrderCommand;
import com.example.cqrs.command.api.ConfirmOrderCommand;
import com.example.cqrs.command.api.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the <em>command side</em> of CQRS.
 *
 * <p>Endpoints here accept HTTP requests that express an <strong>intent to change state</strong>.
 * Each handler creates a command object and sends it via the {@link CommandGateway}.
 * The gateway routes the command to the appropriate {@code @CommandHandler} on the aggregate.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>Command endpoints return minimal responses (just the new ID or 200 OK) —
 *       they do NOT return the full updated resource. Clients must query the read model
 *       separately (via {@link OrderQueryController}) to get the current state.</li>
 *   <li>The order ID is generated here (client-assigned identity pattern) so the
 *       response can include a {@code Location} header pointing to the new resource
 *       without waiting for a database auto-increment.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandController.class);

    /**
     * Axon's {@link CommandGateway} provides a type-safe, synchronous facade
     * over the underlying {@code CommandBus}. It serialises the command, routes it
     * to the correct handler, and returns the handler's result (or throws on failure).
     */
    private final CommandGateway commandGateway;

    public OrderCommandController(CommandGateway commandGateway) {
        this.commandGateway = commandGateway;
    }

    // =========================================================================
    //  Request / Response DTOs
    // =========================================================================

    /**
     * Request body for placing a new order.
     * Bean Validation annotations ensure the payload is valid before the command is created.
     */
    public record PlaceOrderRequest(
            @NotBlank(message = "productId must not be blank")
            String productId,

            @Positive(message = "quantity must be positive")
            int quantity,

            @NotNull(message = "unitPrice must not be null")
            @Positive(message = "unitPrice must be positive")  // validation is done via @Positive on BigDecimal
            BigDecimal unitPrice
    ) {}

    /**
     * Request body for cancelling an order.
     */
    public record CancelOrderRequest(String reason) {}

    /**
     * Response body returned after placing an order.
     * The {@code orderId} allows the client to query the order's status.
     */
    public record PlaceOrderResponse(String orderId, String message) {}

    // =========================================================================
    //  Command endpoints
    // =========================================================================

    /**
     * POST /api/orders — places a new order.
     *
     * <p>Flow:
     * <ol>
     *   <li>Generate a UUID for the new order</li>
     *   <li>Build a {@link PlaceOrderCommand} and send it via {@link CommandGateway}</li>
     *   <li>Axon routes it to {@code OrderAggregate(PlaceOrderCommand)} constructor</li>
     *   <li>The aggregate emits {@code OrderPlacedEvent}; the projection updates the read model</li>
     *   <li>Return 201 Created with Location header</li>
     * </ol>
     *
     * @param request the incoming JSON body
     * @return 201 Created with Location header pointing to the new order
     */
    @PostMapping
    public ResponseEntity<PlaceOrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        // Generate a client-assigned UUID for the new aggregate instance
        String orderId = UUID.randomUUID().toString();

        log.info("Sending PlaceOrderCommand: orderId={}, product={}, qty={}",
                orderId, request.productId(), request.quantity());

        // sendAndWait() blocks until the command handler completes (or throws)
        commandGateway.sendAndWait(new PlaceOrderCommand(
                orderId,
                request.productId(),
                request.quantity(),
                request.unitPrice()
        ));

        // Return 201 Created with a Location header for the new order's query endpoint
        return ResponseEntity
                .created(URI.create("/api/orders/" + orderId))
                .body(new PlaceOrderResponse(orderId, "Order placed successfully"));
    }

    /**
     * PUT /api/orders/{orderId}/confirm — confirms a placed order.
     *
     * <p>The order must be in {@code PLACED} status; otherwise the aggregate
     * throws an {@link IllegalStateException} which Axon propagates back here.
     *
     * @param orderId path variable identifying the order to confirm
     * @return 200 OK on success
     */
    @PutMapping("/{orderId}/confirm")
    public ResponseEntity<Void> confirmOrder(@PathVariable String orderId) {
        log.info("Sending ConfirmOrderCommand: orderId={}", orderId);

        commandGateway.sendAndWait(new ConfirmOrderCommand(orderId));

        return ResponseEntity.ok().build();
    }

    /**
     * PUT /api/orders/{orderId}/cancel — cancels a placed order.
     *
     * <p>A confirmed order cannot be cancelled. If the order is already confirmed,
     * the aggregate throws and the error is propagated to the client.
     *
     * @param orderId path variable identifying the order to cancel
     * @param request optional JSON body containing a cancellation reason
     * @return 200 OK on success
     */
    @PutMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable String orderId,
            @RequestBody(required = false) CancelOrderRequest request) {

        String reason = (request != null) ? request.reason() : null;
        log.info("Sending CancelOrderCommand: orderId={}, reason={}", orderId, reason);

        commandGateway.sendAndWait(new CancelOrderCommand(orderId, reason));

        return ResponseEntity.ok().build();
    }
}
