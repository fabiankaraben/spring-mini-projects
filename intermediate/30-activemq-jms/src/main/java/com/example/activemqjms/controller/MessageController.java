package com.example.activemqjms.controller;

import com.example.activemqjms.domain.OrderMessage;
import com.example.activemqjms.dto.OrderRequest;
import com.example.activemqjms.service.MessageProducerService;
import com.example.activemqjms.service.OrderProcessingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes the JMS message publishing and consumption summary endpoints.
 *
 * <p>This controller is the HTTP entry point for external clients that want to:
 * <ul>
 *   <li>Publish an order message to the ActiveMQ queue ({@code POST /api/messages/orders}).</li>
 *   <li>Check which order messages have been consumed and processed
 *       ({@code GET /api/messages/orders}).</li>
 * </ul>
 *
 * <h2>Responsibilities of this class</h2>
 * <ul>
 *   <li>Map HTTP verbs and URL paths to service methods.</li>
 *   <li>Trigger Bean Validation on the request body via {@code @Valid}.</li>
 *   <li>Convert service results to the correct HTTP status codes and JSON bodies.</li>
 * </ul>
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 *   POST /api/messages/orders
 *       Body : { "orderId": "...", "product": "...", "quantity": N }
 *       → 202 Accepted with the published {@link OrderMessage} as JSON body
 *
 *   GET  /api/messages/orders
 *       → 200 OK with a list of all processed {@link OrderMessage}s
 * </pre>
 *
 * <h2>Why HTTP 202 Accepted for the POST?</h2>
 * <p>HTTP 202 Accepted is the semantically correct response for an asynchronous
 * operation: it tells the client "I have received your request and handed it
 * off for processing, but the processing has not completed yet." The message
 * has been published to ActiveMQ, but no consumer has processed it at the time
 * this response is sent.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageProducerService messageProducerService;
    private final OrderProcessingService orderProcessingService;

    /**
     * Constructor injection makes service dependencies explicit and enables
     * unit testing without loading the full Spring context.
     *
     * @param messageProducerService the service that publishes messages to ActiveMQ
     * @param orderProcessingService the service that tracks processed orders
     */
    public MessageController(MessageProducerService messageProducerService,
                              OrderProcessingService orderProcessingService) {
        this.messageProducerService = messageProducerService;
        this.orderProcessingService = orderProcessingService;
    }

    // ── POST /api/messages/orders ─────────────────────────────────────────────────

    /**
     * Publish an order message to the ActiveMQ queue.
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

    // ── GET /api/messages/orders ──────────────────────────────────────────────────

    /**
     * Return a list of all order messages that have been consumed and processed.
     *
     * <p>This endpoint is useful for verifying the end-to-end flow:
     * after publishing an order via {@code POST /api/messages/orders}, the
     * JMS listener picks it up asynchronously and adds it to the processed list.
     * Polling this endpoint allows you to confirm that the round-trip completed.
     *
     * <p>Note: there may be a short delay between publishing and the order appearing
     * in this list, because message delivery is asynchronous.
     *
     * @return 200 OK with a JSON array of all processed {@link OrderMessage}s
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderMessage>> getProcessedOrders() {
        List<OrderMessage> processed = orderProcessingService.getProcessedOrders();
        return ResponseEntity.ok(processed);
    }
}
