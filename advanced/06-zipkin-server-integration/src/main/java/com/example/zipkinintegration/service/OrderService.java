package com.example.zipkinintegration.service;

import com.example.zipkinintegration.domain.Order;
import com.example.zipkinintegration.domain.OrderStatus;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core business service for managing {@link Order} objects.
 *
 * <h2>Tracing</h2>
 * <p>Every public method creates a dedicated child span so that individual
 * service operations appear as distinct segments in the Zipkin waterfall.
 * This lets you see exactly how long each step takes:
 * <pre>
 *   GET /api/orders/{id}
 *   └── order-service.getOrderById   ← this span
 *       └── (no sub-calls for reads)
 *
 *   POST /api/orders
 *   └── order-service.createOrder    ← this span
 *       └── inventory.checkAvailability  ← child span in InventoryService
 * </pre>
 *
 * <h2>Storage</h2>
 * <p>Orders are stored in an in-memory {@link ConcurrentHashMap} so the
 * project runs without a database. A real application would use a JPA
 * repository or another persistence mechanism.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * In-memory store that maps order ID → Order.
     * {@link ConcurrentHashMap} is used so the store is thread-safe.
     */
    private final Map<Long, Order> store = new ConcurrentHashMap<>();

    /**
     * Auto-incrementing ID counter. {@link AtomicLong} is thread-safe
     * and guarantees unique IDs even under concurrent requests.
     */
    private final AtomicLong idSequence = new AtomicLong(1);

    /**
     * Micrometer Tracer – auto-configured when
     * {@code micrometer-tracing-bridge-brave} is on the classpath.
     */
    private final Tracer tracer;

    /**
     * Sub-service that checks whether the requested inventory is available.
     * Calling it from here demonstrates a parent → child span relationship.
     */
    private final InventoryService inventoryService;

    public OrderService(Tracer tracer, InventoryService inventoryService) {
        this.tracer = tracer;
        this.inventoryService = inventoryService;
    }

    /**
     * Returns a snapshot of all orders currently in the store.
     *
     * <p>A child span named {@code "order-service.getAllOrders"} is opened
     * for this operation so list queries are visible in Zipkin traces.
     *
     * @return list of all orders (may be empty)
     */
    public List<Order> getAllOrders() {
        Span span = tracer.nextSpan().name("order-service.getAllOrders").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            log.info("Fetching all orders, current count={}", store.size());
            // Tag the span with the number of results for observability
            span.tag("order.count", String.valueOf(store.size()));
            return new ArrayList<>(store.values());
        } finally {
            span.end();
        }
    }

    /**
     * Looks up a single order by its ID.
     *
     * <p>A child span named {@code "order-service.getOrderById"} is opened.
     * If the order is not found, the span is tagged with {@code "not_found"}
     * so operators can filter for those events in Zipkin.
     *
     * @param id the order ID to look up
     * @return an {@link Optional} containing the order, or empty if not found
     */
    public Optional<Order> getOrderById(Long id) {
        Span span = tracer.nextSpan().name("order-service.getOrderById").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("order.id", String.valueOf(id));
            log.info("Looking up order id={}", id);
            Optional<Order> result = Optional.ofNullable(store.get(id));
            // Tag the span so Zipkin searches can filter "not found" lookups
            span.tag("order.found", String.valueOf(result.isPresent()));
            return result;
        } finally {
            span.end();
        }
    }

    /**
     * Creates a new order after verifying inventory availability.
     *
     * <p>This method demonstrates a <em>multi-span</em> trace:
     * <ol>
     *   <li>A child span {@code "order-service.createOrder"} is started here.</li>
     *   <li>{@link InventoryService#checkAvailability} starts its own grandchild
     *       span {@code "inventory.checkAvailability"}.</li>
     * </ol>
     * <p>If inventory is unavailable the order is created with status
     * {@link OrderStatus#CANCELLED} so the caller can see the rejection reason.
     *
     * @param product  product name or SKU
     * @param quantity number of units requested
     * @return the newly created order
     */
    public Order createOrder(String product, int quantity) {
        Span span = tracer.nextSpan().name("order-service.createOrder").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("order.product", product);
            span.tag("order.quantity", String.valueOf(quantity));
            log.info("Creating order for product='{}', quantity={}", product, quantity);

            // Delegate to the inventory service – this call creates a grandchild span
            boolean available = inventoryService.checkAvailability(product, quantity);

            // Determine the initial status based on inventory result
            OrderStatus status = available ? OrderStatus.CONFIRMED : OrderStatus.CANCELLED;
            span.tag("order.status", status.name());

            // Assign a unique ID and persist in the in-memory store
            Long id = idSequence.getAndIncrement();
            Order order = new Order(id, product, quantity, status);
            store.put(id, order);

            log.info("Order created: id={}, status={}", id, status);
            return order;
        } finally {
            span.end();
        }
    }

    /**
     * Updates the status of an existing order.
     *
     * <p>A child span {@code "order-service.updateOrderStatus"} tracks this
     * operation in Zipkin, with tags recording the old and new status values.
     *
     * @param id        the ID of the order to update
     * @param newStatus the new status to apply
     * @return an {@link Optional} with the updated order, or empty if not found
     */
    public Optional<Order> updateOrderStatus(Long id, OrderStatus newStatus) {
        Span span = tracer.nextSpan().name("order-service.updateOrderStatus").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            span.tag("order.id", String.valueOf(id));
            span.tag("order.new_status", newStatus.name());
            log.info("Updating status of order id={} to {}", id, newStatus);

            Order order = store.get(id);
            if (order == null) {
                span.tag("order.found", "false");
                return Optional.empty();
            }

            // Record the previous status as a span tag for audit visibility
            span.tag("order.old_status", order.getStatus().name());
            span.tag("order.found", "true");
            order.setStatus(newStatus);
            log.info("Order id={} status updated to {}", id, newStatus);
            return Optional.of(order);
        } finally {
            span.end();
        }
    }

    /**
     * Returns the current trace ID from the active span context.
     *
     * <p>This is used by the controller to include the trace ID in the
     * HTTP response, making it easy to navigate to the trace in Zipkin.
     *
     * @return the 128-bit trace ID as a hex string, or {@code "no-trace"} if
     *         no active span exists (e.g. in unit tests without a tracer)
     */
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return "no-trace";
        }
        String traceId = currentSpan.context().traceId();
        // Return "no-trace" for the NOOP tracer used in unit tests
        return (traceId == null || traceId.isBlank()) ? "no-trace" : traceId;
    }
}
