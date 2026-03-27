package com.example.elasticlogging.controller;

import com.example.elasticlogging.dto.CreateOrderRequest;
import com.example.elasticlogging.dto.UpdateOrderStatusRequest;
import com.example.elasticlogging.model.Order;
import com.example.elasticlogging.service.OrderService;
import jakarta.validation.Valid;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.UUID;

/**
 * REST controller for the Order API.
 *
 * <h2>Logging strategy at the controller layer</h2>
 *
 * <p>The controller is responsible for:
 * <ol>
 *   <li><b>MDC request correlation</b> — Each incoming request gets a unique
 *       {@code requestId} added to the Mapped Diagnostic Context (MDC).
 *       This ID propagates to every log event emitted during that request,
 *       allowing all log lines for a single HTTP call to be grouped in Kibana
 *       by filtering on {@code requestId}.</li>
 *   <li><b>HTTP-layer structured fields</b> — The controller logs the HTTP method,
 *       path, and response status as structured fields.</li>
 * </ol>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST   /api/orders              — Create a new order</li>
 *   <li>GET    /api/orders              — List all orders</li>
 *   <li>GET    /api/orders/{id}         — Get a specific order</li>
 *   <li>PATCH  /api/orders/{id}/status  — Update order status</li>
 *   <li>POST   /api/orders/{id}/fail    — Simulate processing failure (for demo)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** Service containing all domain logic and structured logging for order operations. */
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order.
     *
     * <p>Demonstrates MDC-based request correlation: a unique {@code requestId} is
     * placed in the MDC before calling the service, then cleared automatically via
     * the try-with-resources idiom. Every log event within this scope — including
     * those emitted by the service — will carry the {@code requestId} field.
     *
     * @param request validated request body
     * @return 201 Created with the new order, or 400 Bad Request on validation failure
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        // Generate a unique request ID and add it to MDC for log correlation.
        // All log events emitted within this try block (including from OrderService)
        // will automatically include "requestId" in the JSON output.
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            log.info("Received create order request",
                    StructuredArguments.kv("httpMethod", "POST"),
                    StructuredArguments.kv("path", "/api/orders"),
                    StructuredArguments.kv("customerId", request.getCustomerId())
            );

            Order order = orderService.createOrder(request);

            log.info("Responding to create order request",
                    StructuredArguments.kv("httpStatus", 201),
                    StructuredArguments.kv("orderId", order.getId())
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        }
    }

    /**
     * Returns all orders in the store.
     *
     * @return 200 OK with an array of orders
     */
    @GetMapping
    public ResponseEntity<Collection<Order>> getAllOrders() {
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            Collection<Order> orders = orderService.getAllOrders();

            log.info("Returning all orders",
                    StructuredArguments.kv("httpMethod", "GET"),
                    StructuredArguments.kv("path", "/api/orders"),
                    StructuredArguments.kv("count", orders.size())
            );

            return ResponseEntity.ok(orders);
        }
    }

    /**
     * Retrieves a single order by its ID.
     *
     * @param id UUID of the order
     * @return 200 OK with the order, or 404 Not Found if it does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            log.info("Received get order request",
                    StructuredArguments.kv("httpMethod", "GET"),
                    StructuredArguments.kv("orderId", id)
            );

            Order order = orderService.getOrder(id);

            return ResponseEntity.ok(order);
        }
    }

    /**
     * Updates the status of an existing order.
     *
     * @param id      UUID of the order to update
     * @param request body containing the new status value
     * @return 200 OK with the updated order
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Order> updateStatus(
            @PathVariable String id,
            @RequestBody @Valid UpdateOrderStatusRequest request) {

        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            log.info("Received update status request",
                    StructuredArguments.kv("httpMethod", "PATCH"),
                    StructuredArguments.kv("orderId", id),
                    StructuredArguments.kv("newStatus", request.getStatus().name())
            );

            Order order = orderService.updateOrderStatus(id, request.getStatus());

            return ResponseEntity.ok(order);
        }
    }

    /**
     * Simulates a processing failure for an order.
     *
     * <p>This endpoint exists to demonstrate ERROR-level structured logging.
     * Calling it will emit an ERROR log event with structured fields that Kibana
     * can use for alerting (e.g. alert when error rate exceeds threshold).
     *
     * @param id UUID of the order to fail
     * @return 200 OK after logging the failure
     */
    @PostMapping("/{id}/fail")
    public ResponseEntity<String> simulateFailure(@PathVariable String id) {
        String requestId = UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            log.info("Simulating processing failure",
                    StructuredArguments.kv("orderId", id)
            );

            orderService.simulateProcessingFailure(id);

            return ResponseEntity.ok("Failure logged for order " + id);
        }
    }
}
