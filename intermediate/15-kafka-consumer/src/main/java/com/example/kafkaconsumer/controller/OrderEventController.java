package com.example.kafkaconsumer.controller;

import com.example.kafkaconsumer.domain.OrderStatus;
import com.example.kafkaconsumer.domain.ProcessedOrderEvent;
import com.example.kafkaconsumer.service.OrderEventProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes endpoints for inspecting consumed Kafka events.
 *
 * <p>This controller provides observability into what the Kafka consumer has
 * received and processed. It is intentionally read-only: no data is created or
 * modified through these endpoints. All events are produced by the Kafka listener.
 *
 * <h2>Endpoint summary</h2>
 * <pre>
 *   GET /api/events            – list all processed order events (optional ?status= filter)
 *   GET /api/events/count      – return the total number of processed events
 * </pre>
 *
 * <h2>Design rationale</h2>
 * <p>In a real production system you would query a persistent store (database)
 * rather than an in-memory list. The in-memory approach is used here to keep
 * the demo focused on Kafka consumer mechanics without introducing a database
 * dependency.
 */
@RestController
@RequestMapping("/api/events")
public class OrderEventController {

    /** Service that stores and provides access to processed events. */
    private final OrderEventProcessor processorService;

    /**
     * Constructor injection – Spring will provide the
     * {@link com.example.kafkaconsumer.service.OrderEventProcessorService} bean automatically
     * (it implements {@link OrderEventProcessor}).
     *
     * @param processorService the service managing processed events
     */
    public OrderEventController(OrderEventProcessor processorService) {
        this.processorService = processorService;
    }

    /**
     * Returns all order events that have been consumed and processed.
     *
     * <p>An optional {@code status} query parameter can be used to filter
     * the results by {@link OrderStatus}. If the parameter is omitted,
     * all processed events are returned.
     *
     * <p>Example requests:
     * <pre>
     *   GET /api/events                     – returns all events
     *   GET /api/events?status=CREATED      – returns only CREATED events
     *   GET /api/events?status=CONFIRMED    – returns only CONFIRMED events
     * </pre>
     *
     * @param status optional filter for order status; case-sensitive enum name
     * @return {@code 200 OK} with a JSON array of {@link ProcessedOrderEvent} objects
     */
    @GetMapping
    public ResponseEntity<List<ProcessedOrderEvent>> getEvents(
            @RequestParam(required = false) String status) {

        // If a status filter was provided, validate and apply it
        if (status != null) {
            try {
                // Convert the string parameter to the enum constant.
                // Throws IllegalArgumentException if the value is not a valid enum name.
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                List<ProcessedOrderEvent> filtered =
                        processorService.getProcessedEventsByStatus(orderStatus);
                return ResponseEntity.ok(filtered);
            } catch (IllegalArgumentException e) {
                // Return 400 Bad Request when the status value is not a valid enum constant
                return ResponseEntity.badRequest().build();
            }
        }

        // No filter: return all processed events
        return ResponseEntity.ok(processorService.getProcessedEvents());
    }

    /**
     * Returns the total number of order events processed so far.
     *
     * <p>This lightweight endpoint is useful for health-checks and dashboards:
     * you can tell at a glance whether the consumer is actively processing
     * messages without fetching the full events list.
     *
     * <p>Example response body:
     * <pre>{@code
     * { "count": 42 }
     * }</pre>
     *
     * @return {@code 200 OK} with a JSON object containing the {@code count} field
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> getCount() {
        // Return the count wrapped in a map so the JSON response has a named field
        return ResponseEntity.ok(Map.of("count", processorService.getProcessedCount()));
    }
}
