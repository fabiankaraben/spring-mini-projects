package com.example.zipkinintegration.unit;

import com.example.zipkinintegration.service.InventoryService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryService}.
 *
 * <p>These tests exercise the inventory availability logic in isolation –
 * no Spring context, no Docker container, no actual tracing infrastructure.
 *
 * <h2>Mocking the Tracer</h2>
 * <p>The {@link Tracer} and its collaborator objects ({@link Span},
 * {@link Tracer.SpanInScope}) are mocked with Mockito so the tests focus on
 * <em>business logic</em> without starting Zipkin or the Brave reporter.
 * This keeps unit tests fast (milliseconds) and deterministic.
 *
 * <h2>Test design</h2>
 * <ul>
 *   <li>Happy path: product names that do NOT start with "unavailable" are
 *       considered in stock.</li>
 *   <li>Out-of-stock path: product names starting with "unavailable" return
 *       {@code false} from {@code checkAvailability}.</li>
 *   <li>Span lifecycle: verifies that {@link Span#end()} is always called
 *       (even in the out-of-stock path) to prevent span leaks.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService – Unit Tests")
class InventoryServiceTest {

    /**
     * Mocked Tracer – returns a mock Span and SpanInScope so no actual
     * tracing infrastructure is needed during tests.
     */
    @Mock
    private Tracer tracer;

    /**
     * Mocked Span – captures tag and end() calls for verification.
     * The Span interface has fluent methods name()/start()/tag() that all return
     * the same Span instance, enabling the chained call pattern.
     */
    @Mock
    private Span span;

    /**
     * Mocked SpanInScope – returned by tracer.withSpan(); implements
     * AutoCloseable so it can be used in try-with-resources in the service.
     */
    @Mock
    private Tracer.SpanInScope spanInScope;

    /** The class under test. */
    private InventoryService inventoryService;

    /**
     * Wires up the mock chain before each test.
     *
     * <p>The Micrometer Tracing API is:
     * {@code tracer.nextSpan()} → returns a {@link Span} directly (not a Builder).
     * That {@link Span} has fluent methods: {@code .name("...").start()} which both
     * return the same {@link Span} instance.
     *
     * <p>Chain: tracer.nextSpan() → span; span.name() → span; span.start() → span
     * tracer.withSpan(span) → spanInScope
     */
    @BeforeEach
    void setUp() {
        // tracer.nextSpan() returns a Span directly; Span has fluent .name() and .start()
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);

        // span.tag() must return the span itself (fluent interface)
        when(span.tag(anyString(), anyString())).thenReturn(span);

        // tracer.withSpan(span) returns the SpanInScope used by try-with-resources
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        inventoryService = new InventoryService(tracer);
    }

    // ── checkAvailability – available products ────────────────────────────

    @Test
    @DisplayName("checkAvailability should return true for a normal product name")
    void checkAvailability_shouldReturnTrue_forNormalProduct() {
        boolean result = inventoryService.checkAvailability("laptop-pro-15", 2);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkAvailability should return true for a product with mixed case")
    void checkAvailability_shouldReturnTrue_forMixedCaseProduct() {
        // "LAPTOP" does not start with "unavailable" (case-insensitive check)
        boolean result = inventoryService.checkAvailability("LAPTOP", 1);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("checkAvailability should return true for any positive quantity")
    void checkAvailability_shouldReturnTrue_forHighQuantity() {
        boolean result = inventoryService.checkAvailability("widget", 999);

        assertThat(result).isTrue();
    }

    // ── checkAvailability – unavailable products ──────────────────────────

    @Test
    @DisplayName("checkAvailability should return false for a product starting with 'unavailable'")
    void checkAvailability_shouldReturnFalse_forUnavailableProduct() {
        boolean result = inventoryService.checkAvailability("unavailable-item", 1);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("checkAvailability should return false for 'UNAVAILABLE' (case-insensitive)")
    void checkAvailability_shouldReturnFalse_forUpperCaseUnavailableProduct() {
        boolean result = inventoryService.checkAvailability("UNAVAILABLE-WIDGET", 1);

        assertThat(result).isFalse();
    }

    // ── Span lifecycle ────────────────────────────────────────────────────

    @Test
    @DisplayName("checkAvailability should always call span.end() to prevent span leaks")
    void checkAvailability_shouldEndSpan() {
        inventoryService.checkAvailability("some-product", 3);

        // span.end() MUST be called regardless of availability result
        verify(span).end();
    }

    @Test
    @DisplayName("checkAvailability should end span even for unavailable products")
    void checkAvailability_shouldEndSpan_forUnavailableProduct() {
        inventoryService.checkAvailability("unavailable-product", 1);

        verify(span).end();
    }

    @Test
    @DisplayName("checkAvailability should tag span with product name")
    void checkAvailability_shouldTagSpanWithProduct() {
        inventoryService.checkAvailability("keyboard", 1);

        verify(span).tag("product", "keyboard");
    }

    @Test
    @DisplayName("checkAvailability should tag span with requested quantity")
    void checkAvailability_shouldTagSpanWithQuantity() {
        inventoryService.checkAvailability("mouse", 5);

        verify(span).tag("requested.quantity", "5");
    }
}
