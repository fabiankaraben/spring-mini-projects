package com.example.cqrs.rest;

import com.example.cqrs.query.api.FindAllOrdersQuery;
import com.example.cqrs.query.api.FindOrderByIdQuery;
import com.example.cqrs.query.model.OrderSummary;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for the <em>query side</em> of CQRS.
 *
 * <p>Endpoints here handle <strong>read-only</strong> requests. They create query objects
 * and dispatch them via the {@link QueryGateway}, which routes them to the matching
 * {@code @QueryHandler} method in {@code OrderProjection}.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li>All endpoints return data from the read model ({@link OrderSummary} JPA entities),
 *       never from the event store. This keeps reads fast and independent of write load.</li>
 *   <li>We use {@link QueryGateway#query} with {@link ResponseTypes} to specify the
 *       expected return type. Axon uses this type information to select the correct handler.</li>
 *   <li>Queries return {@link CompletableFuture} from the gateway; we join them synchronously
 *       here for simplicity. In a reactive stack these would be returned directly.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryController.class);

    /**
     * Axon's {@link QueryGateway} is the query-side counterpart to {@link QueryGateway}.
     * It routes query messages to the matching {@code @QueryHandler} and returns the result.
     */
    private final QueryGateway queryGateway;

    public OrderQueryController(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    // =========================================================================
    //  Query endpoints
    // =========================================================================

    /**
     * GET /api/orders/{orderId} — retrieves a single order by ID.
     *
     * <p>This endpoint reads from the {@link OrderSummary} read model table.
     * The response reflects the last known state as of the most recent event
     * processed by the {@code OrderProjection}.
     *
     * @param orderId the UUID of the order to retrieve
     * @return 200 OK with the {@link OrderSummary}, or 404 Not Found
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderSummary> getOrder(@PathVariable String orderId) {
        log.debug("Query: FindOrderByIdQuery orderId={}", orderId);

        // Send the query and wait for the Optional<OrderSummary> response
        Optional<OrderSummary> result = queryGateway.query(
                new FindOrderByIdQuery(orderId),
                ResponseTypes.optionalInstanceOf(OrderSummary.class)
        ).join();

        // Map Optional to HTTP 200 / 404
        return result
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/orders — retrieves all orders, optionally filtered by status.
     *
     * <p>Optional query parameter {@code status} can be one of:
     * {@code PLACED}, {@code CONFIRMED}, {@code CANCELLED}.
     * When omitted, all orders are returned.
     *
     * <p>Example: {@code GET /api/orders?status=PLACED}
     *
     * @param status optional status filter
     * @return 200 OK with a list of matching {@link OrderSummary} objects
     */
    @GetMapping
    public ResponseEntity<List<OrderSummary>> getAllOrders(
            @RequestParam(required = false) String status) {

        log.debug("Query: FindAllOrdersQuery status={}", status);

        List<OrderSummary> results = queryGateway.query(
                new FindAllOrdersQuery(status),
                ResponseTypes.multipleInstancesOf(OrderSummary.class)
        ).join();

        return ResponseEntity.ok(results);
    }
}
