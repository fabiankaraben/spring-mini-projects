package com.example.saga.order.web;

import com.example.saga.order.domain.Order;
import com.example.saga.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the Order Service.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code POST /api/orders}    — create a new order and start the saga.</li>
 *   <li>{@code GET  /api/orders/{id}} — poll the current status of an order.</li>
 * </ul>
 *
 * <p>The controller is deliberately thin: all business logic lives in
 * {@link OrderService}. The controller's only responsibility is HTTP mapping
 * (deserializing the request body, returning the correct HTTP status code).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates a new order and initiates the distributed saga.
     *
     * <p>Returns HTTP 201 Created with a {@code Location} header pointing to the
     * newly created resource. The response body contains the full order detail
     * including the initial status ({@code PAYMENT_PROCESSING} after the event
     * is published).
     *
     * @param request validated request body
     * @return 201 with the created order, or 400 if validation fails
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.totalPrice()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.getId())
                .toUri();

        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    /**
     * Retrieves the current state of an order by its UUID.
     *
     * <p>Clients can poll this endpoint to observe saga progress:
     * PAYMENT_PROCESSING → INVENTORY_RESERVING → COMPLETED (or CANCELLED).
     *
     * @param id the UUID of the order
     * @return 200 with the order, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
                .map(OrderResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Request / Response DTOs (inner records — keep them close to the controller)
    // -------------------------------------------------------------------------

    /**
     * Request body for creating an order.
     *
     * <p>All fields are validated with Bean Validation annotations. Spring
     * automatically returns HTTP 400 with a validation error body when any
     * constraint is violated.
     *
     * @param customerId non-blank identifier of the ordering customer
     * @param productId  non-blank identifier of the product
     * @param quantity   must be at least 1
     * @param totalPrice must be positive (> 0)
     */
    public record CreateOrderRequest(
            @NotBlank String customerId,
            @NotBlank String productId,
            @Positive int quantity,
            @Positive BigDecimal totalPrice
    ) {}

    /**
     * Response body returned for all order-related endpoints.
     * Maps directly from an {@link Order} entity, converting types to JSON-friendly forms.
     */
    public record OrderResponse(
            String id,
            String customerId,
            String productId,
            int quantity,
            BigDecimal totalPrice,
            String status,
            String createdAt,
            String updatedAt,
            String failureReason
    ) {
        /**
         * Factory method that converts a domain {@link Order} entity into a
         * REST-layer response record.
         *
         * @param order the domain entity to convert
         * @return the response DTO
         */
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId().toString(),
                    order.getCustomerId(),
                    order.getProductId(),
                    order.getQuantity(),
                    order.getTotalPrice(),
                    order.getStatus().name(),
                    order.getCreatedAt().toString(),
                    order.getUpdatedAt().toString(),
                    order.getFailureReason()
            );
        }
    }
}
