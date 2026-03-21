package com.example.zipkinintegration.controller;

import com.example.zipkinintegration.domain.Order;
import com.example.zipkinintegration.domain.OrderStatus;
import com.example.zipkinintegration.dto.CreateOrderRequest;
import com.example.zipkinintegration.dto.OrderResponse;
import com.example.zipkinintegration.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller that exposes the Order API.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET  /api/orders}        – list all orders</li>
 *   <li>{@code GET  /api/orders/{id}}   – get a single order by ID</li>
 *   <li>{@code POST /api/orders}        – create a new order</li>
 *   <li>{@code PATCH /api/orders/{id}/status} – update an order's status</li>
 * </ul>
 *
 * <h2>Distributed Tracing</h2>
 * <p>Spring Boot's Micrometer Tracing auto-configuration automatically wraps
 * every incoming HTTP request in a root span. You do not need any annotation;
 * the instrumentation is transparent. The trace context is propagated to all
 * downstream service calls via HTTP headers in B3 format:
 * <ul>
 *   <li>{@code X-B3-TraceId}   – 128-bit trace identifier</li>
 *   <li>{@code X-B3-SpanId}    – 64-bit span identifier</li>
 *   <li>{@code X-B3-Sampled}   – sampling decision (1 = sampled)</li>
 * </ul>
 *
 * <p>The trace ID is included in every response body so callers can look up
 * the trace in the Zipkin UI at:
 * {@code http://localhost:9411/zipkin/?traceId=<traceId>}
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────

    /**
     * Returns the list of all orders currently in the system.
     *
     * <p>Each response includes the trace ID of the request so the caller can
     * look up the full trace (including the child span inside
     * {@link com.example.zipkinintegration.service.OrderService#getAllOrders})
     * in the Zipkin UI.
     *
     * @return {@code 200 OK} with a JSON array of {@link OrderResponse} objects
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        // getCurrentTraceId() reads the trace ID from the currently active span
        String traceId = orderService.getCurrentTraceId();
        List<OrderResponse> responses = orderService.getAllOrders()
                .stream()
                .map(order -> new OrderResponse(order, traceId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    /**
     * Looks up a single order by its ID.
     *
     * @param id the order ID taken from the URL path
     * @return {@code 200 OK} with the order, or {@code 404 Not Found}
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        String traceId = orderService.getCurrentTraceId();
        Optional<Order> order = orderService.getOrderById(id);
        return order
                .map(o -> ResponseEntity.ok(new OrderResponse(o, traceId)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    /**
     * Creates a new order.
     *
     * <p>The request body is validated by Bean Validation before the service
     * method is called. If validation fails, Spring returns {@code 400 Bad Request}
     * automatically.
     *
     * <p>This endpoint produces a multi-level trace in Zipkin:
     * <ol>
     *   <li>Root span: {@code POST /api/orders} (created by Spring MVC)</li>
     *   <li>Child span: {@code order-service.createOrder}</li>
     *   <li>Grandchild span: {@code inventory.checkAvailability}</li>
     * </ol>
     *
     * @param request validated request body
     * @return {@code 201 Created} with the new order
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request.getProduct(), request.getQuantity());
        String traceId = orderService.getCurrentTraceId();
        return ResponseEntity.status(HttpStatus.CREATED).body(new OrderResponse(order, traceId));
    }

    // ── PATCH /api/orders/{id}/status ─────────────────────────────────────────

    /**
     * Updates the status of an existing order.
     *
     * <p>The new status is supplied as a query parameter. Example:
     * {@code PATCH /api/orders/1/status?status=SHIPPED}
     *
     * @param id        the order ID
     * @param newStatus the new status value (must be a valid {@link OrderStatus} name)
     * @return {@code 200 OK} with the updated order, or {@code 404 Not Found}
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam("status") OrderStatus newStatus) {
        String traceId = orderService.getCurrentTraceId();
        Optional<Order> updated = orderService.updateOrderStatus(id, newStatus);
        return updated
                .map(o -> ResponseEntity.ok(new OrderResponse(o, traceId)))
                .orElse(ResponseEntity.notFound().build());
    }
}
