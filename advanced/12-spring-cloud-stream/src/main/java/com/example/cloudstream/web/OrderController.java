package com.example.cloudstream.web;

import com.example.cloudstream.domain.Order;
import com.example.cloudstream.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller that exposes the Order API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST   /api/orders}       — place a new order</li>
 *   <li>{@code GET    /api/orders/{id}}  — get a single order by UUID</li>
 *   <li>{@code GET    /api/orders}       — list all orders</li>
 * </ul>
 *
 * <p>When a client POSTs an order:
 * <ol>
 *   <li>Spring MVC validates the request body using Bean Validation.</li>
 *   <li>{@link OrderService#placeOrder} creates the {@link Order}, saves it, and
 *       enqueues an {@code OrderPlacedEvent} for the Spring Cloud Stream supplier.</li>
 *   <li>The controller returns HTTP 201 Created with a {@code Location} header
 *       pointing to the newly created resource.</li>
 *   <li>In the background, Spring Cloud Stream picks up the event and drives it
 *       through the Kafka pipeline (producer → processor → consumer).</li>
 * </ol>
 *
 * <p>Clients can poll {@code GET /api/orders/{id}} to observe the status
 * transition from PENDING → PROCESSING → NOTIFIED (or REJECTED).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places a new order.
     *
     * <p>The {@link Valid} annotation triggers Bean Validation on the request body.
     * If validation fails, Spring returns HTTP 400 with a structured error response
     * before this method is even called.
     *
     * @param request the order details
     * @return HTTP 201 Created with the order response body and a Location header
     */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        Order order = orderService.placeOrder(
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.totalPrice()
        );

        // Build the Location URI for the newly created resource:
        // e.g. http://localhost:8080/api/orders/123e4567-e89b-12d3-a456-426614174000
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    /**
     * Retrieves a single order by its UUID.
     *
     * @param id the order UUID (parsed from the path variable)
     * @return HTTP 200 with the order body, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists all orders currently in the in-memory store.
     *
     * <p>Useful for observing status transitions across multiple orders during a demo.
     *
     * @return HTTP 200 with a JSON array of all orders
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> listOrders() {
        List<OrderResponse> responses = orderService.findAll().stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
