package com.example.rabbitmqproducer.controller;

import com.example.rabbitmqproducer.domain.OrderMessage;
import com.example.rabbitmqproducer.dto.OrderRequest;
import com.example.rabbitmqproducer.service.MessageProducerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes the message publishing endpoint.
 *
 * <p>This controller is the HTTP entry point for external clients that want
 * to place orders by publishing a message to RabbitMQ. It delegates all
 * messaging logic to {@link MessageProducerService}.
 *
 * <h2>Responsibilities of this class</h2>
 * <ul>
 *   <li>Map the HTTP POST verb and URL path to the service method.</li>
 *   <li>Trigger Bean Validation on the request body via {@code @Valid}.</li>
 *   <li>Convert the service result to the correct HTTP status code and JSON
 *       response body.</li>
 * </ul>
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 *   POST /api/messages/orders
 *       Body : { "orderId": "...", "product": "...", "quantity": N }
 *       → 202 Accepted with the published {@link OrderMessage} as JSON body
 * </pre>
 *
 * <h2>Why HTTP 202 Accepted?</h2>
 * <p>HTTP 202 Accepted is the semantically correct response for an asynchronous
 * operation: it tells the client "I have received your request and handed it
 * off for processing, but the processing has not completed yet." The message
 * has been published to RabbitMQ, but no consumer has processed it at the time
 * this response is sent.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageProducerService messageProducerService;

    /**
     * Constructor injection makes the service dependency explicit and enables
     * unit testing without loading the full Spring context.
     *
     * @param messageProducerService the service that publishes messages to RabbitMQ
     */
    public MessageController(MessageProducerService messageProducerService) {
        this.messageProducerService = messageProducerService;
    }

    // ── POST /api/messages/orders ─────────────────────────────────────────────────

    /**
     * Publish an order message to the RabbitMQ exchange.
     *
     * <p>{@code @Valid} activates Bean Validation on the {@link OrderRequest}
     * body. If any constraint is violated (e.g. blank {@code orderId}, zero
     * {@code quantity}), Spring MVC returns HTTP 400 Bad Request with
     * field-level error details before this method body is executed.
     *
     * <p>On success the response body contains the {@link OrderMessage} that
     * was published, including the auto-generated {@code messageId} and
     * {@code createdAt} fields. This lets the client correlate the HTTP call
     * with broker-level traces if needed.
     *
     * @param request the order request from the HTTP body (JSON), validated by Bean Validation
     * @return 202 Accepted with the published {@link OrderMessage} as the response body
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderMessage> publishOrder(@Valid @RequestBody OrderRequest request) {
        // Delegate to the service — all messaging logic lives there
        OrderMessage published = messageProducerService.publishOrder(request);

        // HTTP 202 Accepted: the request has been accepted for asynchronous processing
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(published);
    }
}
