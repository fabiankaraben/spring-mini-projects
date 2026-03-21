package com.example.zipkinintegration.unit;

import com.example.zipkinintegration.domain.Order;
import com.example.zipkinintegration.domain.OrderStatus;
import com.example.zipkinintegration.service.InventoryService;
import com.example.zipkinintegration.service.OrderService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>All collaborators ({@link Tracer} and {@link InventoryService}) are
 * mocked with Mockito so the tests run fast, without a Spring context,
 * without Docker, and without actual tracing infrastructure.
 *
 * <h2>What is tested</h2>
 * <ul>
 *   <li>{@link OrderService#createOrder} – happy path (stock available) and
 *       cancellation path (stock unavailable).</li>
 *   <li>{@link OrderService#getAllOrders} – empty list and after insertions.</li>
 *   <li>{@link OrderService#getOrderById} – found and not-found cases.</li>
 *   <li>{@link OrderService#updateOrderStatus} – update and not-found cases.</li>
 *   <li>{@link OrderService#getCurrentTraceId} – delegates to active span.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService – Unit Tests")
class OrderServiceTest {

    /**
     * Mocked Micrometer Tracer. Returns a Span.Builder chain so the service
     * can call tracer.nextSpan().name("...").start() without NPEs.
     */
    @Mock
    private Tracer tracer;

    /**
     * Mocked InventoryService. Controls whether stock is available for
     * a given product/quantity combination.
     */
    @Mock
    private InventoryService inventoryService;

    /**
     * Mocked Span – returned by the span builder chain in setUp().
     */
    @Mock
    private Span span;

    /**
     * Mocked SpanInScope – returned by tracer.withSpan(span).
     * Implements AutoCloseable so it works in try-with-resources.
     */
    @Mock
    private Tracer.SpanInScope spanInScope;

    /** The class under test, created fresh before each test. */
    private OrderService orderService;

    /**
     * Sets up the mock chain for the Tracer before each test method.
     *
     * <p>The Micrometer Tracing API: {@code tracer.nextSpan()} returns a {@link Span}
     * directly (not a Builder). {@link Span} itself has fluent methods:
     * {@code .name("...").start()} which both return the same {@link Span} instance.
     *
     * <p>Chain: tracer.nextSpan() → span; span.name(…) → span; span.start() → span
     */
    @BeforeEach
    void setUp() {
        // These stubs are shared across all tests. Some tests (e.g. getCurrentTraceId)
        // do not trigger span-creating code paths, so Mockito's strict mode would flag
        // these stubs as "unnecessary". lenient() opts out of that check for shared
        // @BeforeEach stubs while keeping strict verification everywhere else.
        lenient().when(tracer.nextSpan()).thenReturn(span);
        lenient().when(span.name(anyString())).thenReturn(span);
        lenient().when(span.start()).thenReturn(span);

        // span.tag() must return the span itself (fluent builder pattern)
        lenient().when(span.tag(anyString(), anyString())).thenReturn(span);

        // tracer.withSpan(span) provides the try-with-resources scope
        lenient().when(tracer.withSpan(span)).thenReturn(spanInScope);

        orderService = new OrderService(tracer, inventoryService);
    }

    // ── createOrder ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder should return CONFIRMED order when inventory is available")
    void createOrder_shouldReturnConfirmedOrder_whenInventoryAvailable() {
        // Arrange: inventory says yes
        when(inventoryService.checkAvailability("keyboard", 2)).thenReturn(true);

        // Act
        Order order = orderService.createOrder("keyboard", 2);

        // Assert
        assertThat(order).isNotNull();
        assertThat(order.getId()).isNotNull();
        assertThat(order.getProduct()).isEqualTo("keyboard");
        assertThat(order.getQuantity()).isEqualTo(2);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("createOrder should return CANCELLED order when inventory is unavailable")
    void createOrder_shouldReturnCancelledOrder_whenInventoryUnavailable() {
        // Arrange: inventory says no
        when(inventoryService.checkAvailability("unavailable-item", 1)).thenReturn(false);

        // Act
        Order order = orderService.createOrder("unavailable-item", 1);

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getProduct()).isEqualTo("unavailable-item");
    }

    @Test
    @DisplayName("createOrder should assign unique IDs to consecutive orders")
    void createOrder_shouldAssignUniqueIds() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);

        Order first = orderService.createOrder("monitor", 1);
        Order second = orderService.createOrder("mouse", 2);

        // IDs must be different (auto-incremented)
        assertThat(first.getId()).isNotEqualTo(second.getId());
        // Second ID must be greater than the first (monotonically increasing)
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    @DisplayName("createOrder should persist the order so it appears in getAllOrders")
    void createOrder_shouldPersistOrder() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);

        Order created = orderService.createOrder("headset", 1);
        List<Order> all = orderService.getAllOrders();

        assertThat(all).anyMatch(o -> o.getId().equals(created.getId()));
    }

    // ── getAllOrders ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllOrders should return empty list when no orders exist")
    void getAllOrders_shouldReturnEmptyList_initially() {
        List<Order> orders = orderService.getAllOrders();

        assertThat(orders).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getAllOrders should return all created orders")
    void getAllOrders_shouldReturnAllOrders() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);

        orderService.createOrder("product-a", 1);
        orderService.createOrder("product-b", 3);

        List<Order> orders = orderService.getAllOrders();

        assertThat(orders).hasSize(2);
    }

    // ── getOrderById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById should return the order when it exists")
    void getOrderById_shouldReturnOrder_whenExists() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);
        Order created = orderService.createOrder("laptop", 1);

        Optional<Order> found = orderService.getOrderById(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProduct()).isEqualTo("laptop");
    }

    @Test
    @DisplayName("getOrderById should return empty Optional for a non-existent ID")
    void getOrderById_shouldReturnEmpty_whenNotExists() {
        Optional<Order> found = orderService.getOrderById(999L);

        assertThat(found).isEmpty();
    }

    // ── updateOrderStatus ─────────────────────────────────────────────────

    @Test
    @DisplayName("updateOrderStatus should update the status of an existing order")
    void updateOrderStatus_shouldUpdateStatus_whenOrderExists() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);
        Order created = orderService.createOrder("tablet", 2);

        Optional<Order> updated = orderService.updateOrderStatus(created.getId(), OrderStatus.SHIPPED);

        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("updateOrderStatus should return empty Optional for a non-existent order")
    void updateOrderStatus_shouldReturnEmpty_whenOrderNotFound() {
        Optional<Order> result = orderService.updateOrderStatus(42L, OrderStatus.DELIVERED);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateOrderStatus should allow transitioning through multiple statuses")
    void updateOrderStatus_shouldAllowMultipleTransitions() {
        when(inventoryService.checkAvailability(anyString(), anyInt())).thenReturn(true);
        Order created = orderService.createOrder("speaker", 1);

        orderService.updateOrderStatus(created.getId(), OrderStatus.SHIPPED);
        Optional<Order> delivered = orderService.updateOrderStatus(created.getId(), OrderStatus.DELIVERED);

        assertThat(delivered).isPresent();
        assertThat(delivered.get().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    // ── getCurrentTraceId ─────────────────────────────────────────────────

    @Test
    @DisplayName("getCurrentTraceId should return 'no-trace' when no active span exists")
    void getCurrentTraceId_shouldReturnNoTrace_whenNoActiveSpan() {
        // By default the mock returns null for currentSpan()
        when(tracer.currentSpan()).thenReturn(null);

        String traceId = orderService.getCurrentTraceId();

        assertThat(traceId).isEqualTo("no-trace");
    }

    @Test
    @DisplayName("getCurrentTraceId should return the trace ID from the active span")
    void getCurrentTraceId_shouldReturnTraceId_whenActiveSpanExists() {
        // Arrange: a span with a real trace ID is active.
        // Span.context() returns a TraceContext (not Span.Context) in Micrometer Tracing.
        Span activeSpan = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(ctx.traceId()).thenReturn("abc123def456abc1");
        when(activeSpan.context()).thenReturn(ctx);
        when(tracer.currentSpan()).thenReturn(activeSpan);

        String traceId = orderService.getCurrentTraceId();

        assertThat(traceId).isEqualTo("abc123def456abc1");
    }
}
