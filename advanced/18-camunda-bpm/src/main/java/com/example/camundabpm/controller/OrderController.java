package com.example.camundabpm.controller;

import com.example.camundabpm.domain.Order;
import com.example.camundabpm.domain.OrderStatus;
import com.example.camundabpm.dto.CreateOrderRequest;
import com.example.camundabpm.dto.OrderResponse;
import com.example.camundabpm.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Order fulfilment API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/orders — submit a new order and start the Camunda fulfilment process</li>
 *   <li>GET  /api/orders — list all orders (optionally filtered by status)</li>
 *   <li>GET  /api/orders/{id} — get a single order by ID</li>
 * </ul>
 *
 * <p>The controller is deliberately thin — it only handles HTTP concerns (validation,
 * status codes, response mapping). All business logic lives in {@link OrderService}.
 *
 * <p>Response mapping:
 * Every {@link Order} entity is mapped to an {@link OrderResponse} DTO before being
 * returned. This shields internal domain changes from the public API contract.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Submits a new order and synchronously runs the entire Camunda fulfilment process.
     *
     * <p>Because all service tasks in the process are synchronous, the HTTP response
     * includes the final order status (COMPLETED or FAILED) rather than just PENDING.
     * The client gets the full result in a single HTTP round-trip.
     *
     * <p>HTTP 201 Created is returned on success. The response body contains the
     * order with its final status, tracking number, and process instance ID.
     *
     * @param request the validated order creation request
     * @return 201 Created with the completed order, or 400 if validation fails
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createAndStartOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }

    /**
     * Retrieves all orders, optionally filtered by status.
     *
     * <p>Examples:
     * <ul>
     *   <li>GET /api/orders — returns all orders</li>
     *   <li>GET /api/orders?status=COMPLETED — returns only completed orders</li>
     *   <li>GET /api/orders?status=FAILED — returns only failed orders</li>
     * </ul>
     *
     * @param status optional query parameter to filter by {@link OrderStatus}
     * @return 200 OK with a list of order responses (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status) {

        List<Order> orders = (status != null)
                ? orderService.findByStatus(status)
                : orderService.findAll();

        List<OrderResponse> responses = orders.stream()
                .map(OrderResponse::from)
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Retrieves a single order by its ID.
     *
     * @param id the order's primary key
     * @return 200 OK with the order, or 404 Not Found if no order exists with that ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable Long id) {
        return orderService.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
