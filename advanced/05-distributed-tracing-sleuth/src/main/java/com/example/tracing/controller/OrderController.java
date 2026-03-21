package com.example.tracing.controller;

import com.example.tracing.model.Order;
import com.example.tracing.model.OrderResult;
import com.example.tracing.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for order placement.
 *
 * <p><b>Tracing flow for POST /orders:</b>
 * <ol>
 *   <li>The request arrives at the embedded Tomcat server.</li>
 *   <li>Spring MVC's Micrometer Observation instrumentation (via the
 *       {@code ServerHttpObservationFilter}) creates a root span named
 *       {@code "http POST /orders"} and stores it in the request context.</li>
 *   <li>The span's traceId and spanId are injected into the SLF4J MDC
 *       automatically, so every log statement in this request's thread shows them:
 *       {@code [traceId=abc123 spanId=def456]}.</li>
 *   <li>This controller delegates to {@link OrderService#process(Order)}, which
 *       creates a child span {@code "process-order"} and calls the InventoryService
 *       via HTTP — propagating the trace context automatically.</li>
 *   <li>When this method returns, Spring MVC ends the root span and exports it
 *       (along with all child spans) to Zipkin.</li>
 * </ol>
 *
 * <p>The response body ({@link OrderResult}) includes the traceId and spanId so
 * the caller can immediately look up the trace in the Zipkin UI at
 * http://localhost:9411.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /** Service handling the business logic and the cross-service inventory call. */
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places a new order.
     *
     * <p>The request body is validated with Bean Validation ({@code @Valid}).
     * If validation fails, Spring returns HTTP 400 before this method is invoked.
     *
     * <p>On success, returns HTTP 201 Created with the {@link OrderResult} body.
     * The result includes the traceId that can be used in the Zipkin UI to inspect
     * the full multi-span trace.
     *
     * @param order the order details from the request body (JSON)
     * @return HTTP 201 with {@link OrderResult} body
     */
    @PostMapping
    public ResponseEntity<OrderResult> placeOrder(@Valid @RequestBody Order order) {
        log.info("Received order request [orderId={} productId={} qty={}]",
                order.orderId(), order.productId(), order.quantity());

        OrderResult result = orderService.process(order);

        log.info("Order accepted [orderId={} status={} traceId={}]",
                result.orderId(), result.status(), result.traceId());

        // Return 201 Created — the order has been accepted into the system
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
