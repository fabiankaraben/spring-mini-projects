package com.example.kafkaproducer.controller;

import com.example.kafkaproducer.dto.PublishOrderRequest;
import com.example.kafkaproducer.dto.PublishOrderResponse;
import com.example.kafkaproducer.service.OrderEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes the Kafka producer API.
 *
 * <p>This controller is intentionally thin: it handles only HTTP concerns
 * (request binding, validation, HTTP status codes) and delegates all
 * business logic to {@link OrderEventService}. This separation makes the
 * service layer independently testable without starting an HTTP server.
 *
 * <p><strong>Endpoint summary</strong>
 * <pre>
 *   POST /api/orders   – publish an order event to the Kafka topic
 * </pre>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Handles the domain logic: building and publishing the Kafka event. */
    private final OrderEventService orderEventService;

    /**
     * Constructor injection keeps this controller testable in isolation:
     * a mock or stub {@link OrderEventService} can be passed in without
     * starting a Spring context.
     *
     * @param orderEventService the service that publishes order events
     */
    public OrderController(OrderEventService orderEventService) {
        this.orderEventService = orderEventService;
    }

    /**
     * Publishes an order event to the configured Kafka topic.
     *
     * <p>The {@code @Valid} annotation triggers Bean Validation on the
     * request body before the method body executes. If any constraint
     * is violated (e.g. blank {@code orderId}, negative {@code quantity}),
     * Spring returns a {@code 400 Bad Request} automatically via the
     * {@code @ControllerAdvice} in {@code GlobalExceptionHandler}.
     *
     * <p>On success the response body contains the event ID and the Kafka
     * metadata (topic, partition, offset) assigned by the broker.
     *
     * @param request the validated order publish request
     * @return {@code 202 Accepted} with a {@link PublishOrderResponse} body
     */
    @PostMapping
    public ResponseEntity<PublishOrderResponse> publishOrder(
            @RequestBody @Valid PublishOrderRequest request) {

        // Delegate to the service; the service returns Kafka metadata
        PublishOrderResponse response = orderEventService.publish(request);

        // 202 Accepted: the event has been accepted and forwarded to Kafka.
        // This is semantically more accurate than 200 OK because the HTTP
        // response does not mean the downstream consumers have processed it.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
