package com.example.rabbitmqconsumer.controller;

import com.example.rabbitmqconsumer.service.MessageConsumerService;
import com.example.rabbitmqconsumer.service.MessageConsumerService.ProcessedMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes observability endpoints for the consumer.
 *
 * <p>Because this application is a message consumer (not an HTTP API), its REST
 * endpoints serve an <em>operational</em> purpose rather than a business one.
 * They allow operators, testers, and the integration tests to inspect the
 * consumer's state without connecting directly to RabbitMQ.
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 *   GET /api/messages/stats
 *       → 200 OK with message count statistics (JSON)
 *
 *   GET /api/messages/processed
 *       → 200 OK with the list of recently processed messages (JSON)
 * </pre>
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageConsumerService messageConsumerService;

    /**
     * Constructor injection — makes the dependency explicit and enables unit
     * testing without loading the full Spring context.
     *
     * @param messageConsumerService the service that processes consumed messages
     */
    public MessageController(MessageConsumerService messageConsumerService) {
        this.messageConsumerService = messageConsumerService;
    }

    // ── GET /api/messages/stats ────────────────────────────────────────────────────

    /**
     * Return a summary of consumer statistics.
     *
     * <p>The response body is a simple JSON map:
     * <pre>
     * {
     *   "totalProcessed": 42
     * }
     * </pre>
     *
     * <p>This is useful for a quick health check: if {@code totalProcessed} keeps
     * growing while the producer is running, the consumer is working correctly.
     *
     * @return 200 OK with a JSON object containing consumer statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        // Build a simple statistics map that is serialised to JSON by Spring MVC
        Map<String, Object> stats = Map.of(
                "totalProcessed", messageConsumerService.getMessageCount()
        );
        return ResponseEntity.ok(stats);
    }

    // ── GET /api/messages/processed ────────────────────────────────────────────────

    /**
     * Return the list of recently processed messages.
     *
     * <p>The response body is a JSON array of up to 100 processed-message objects.
     * Each object contains the fields from
     * {@link ProcessedMessage}: {@code messageId}, {@code orderId},
     * {@code product}, {@code quantity}, and {@code processedAt}.
     *
     * <p>This endpoint is intended for development, testing, and debugging. In
     * production, processed message state would typically be persisted to a
     * database and exposed via a paginated query endpoint.
     *
     * @return 200 OK with the JSON array of recently processed messages
     */
    @GetMapping("/processed")
    public ResponseEntity<List<ProcessedMessage>> getProcessedMessages() {
        return ResponseEntity.ok(messageConsumerService.getProcessedMessages());
    }
}
