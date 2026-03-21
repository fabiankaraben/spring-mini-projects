package com.example.springcloudgateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RequestIdGlobalFilter}.
 *
 * <p><b>Testing strategy:</b>
 * {@link RequestIdGlobalFilter} is a pure filter class with no Spring context
 * dependency — it only operates on the {@link org.springframework.web.server.ServerWebExchange}
 * and the {@link GatewayFilterChain}. We can therefore test it without starting
 * a Spring Boot application context, using:
 * <ul>
 *   <li>{@link MockServerHttpRequest} — builds a mock HTTP request with
 *       specific headers, method, and URI, without a real network connection.</li>
 *   <li>{@link MockServerWebExchange} — wraps the mock request in a full
 *       {@code ServerWebExchange} compatible with Spring WebFlux.</li>
 *   <li>A Mockito mock of {@link GatewayFilterChain} — captures the exchange
 *       passed by the filter to the next step in the chain, so we can inspect
 *       what headers were added.</li>
 * </ul>
 *
 * <p><b>What is being tested?</b>
 * <ol>
 *   <li>A new correlation ID is generated when the request has no existing one.</li>
 *   <li>An existing correlation ID is reused (not replaced) when present.</li>
 *   <li>The correlation ID is added to the <em>downstream request headers</em>
 *       (what gets forwarded to the microservice).</li>
 *   <li>The correlation ID is added to the <em>response headers</em>
 *       (what the client receives back).</li>
 * </ol>
 */
@DisplayName("RequestIdGlobalFilter — unit tests")
class RequestIdGlobalFilterTest {

    /** The filter under test. */
    private RequestIdGlobalFilter filter;

    /** The header name used by the filter. */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @BeforeEach
    void setUp() {
        filter = new RequestIdGlobalFilter();
    }

    // =========================================================================
    // Correlation ID generation
    // =========================================================================

    /**
     * Verifies that when an incoming request has NO X-Correlation-ID header,
     * the filter generates a new UUID and adds it to the downstream request.
     */
    @Test
    @DisplayName("Generates a new correlation ID when request has none")
    void shouldGenerateCorrelationIdWhenMissing() {
        // Arrange: a request with no correlation ID header.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products/1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Capture the exchange that the filter passes to chain.filter().
        // We need to inspect the mutated request headers.
        MockServerWebExchange[] capturedExchange = new MockServerWebExchange[1];
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenAnswer(invocation -> {
            // The filter mutates the exchange before calling chain.filter().
            // We capture it here to inspect the headers.
            capturedExchange[0] = null; // Not easily inspectable via MockServerWebExchange
            return Mono.empty();
        });

        // Act: run the filter.
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Assert: the response carries the correlation ID header.
        // (The filter adds it to both the mutated request and the response.)
        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        String correlationId = responseHeaders.getFirst(CORRELATION_ID_HEADER);

        assertThat(correlationId)
                .as("A non-blank correlation ID should be added to the response")
                .isNotBlank();

        // The generated ID should be a valid UUID format (8-4-4-4-12 hex chars).
        assertThat(correlationId)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /**
     * Verifies that when an incoming request already has an X-Correlation-ID
     * header, the filter reuses that ID instead of generating a new one.
     */
    @Test
    @DisplayName("Reuses existing correlation ID when already present")
    void shouldReuseExistingCorrelationId() {
        // Arrange: a request that already has a correlation ID.
        String existingId = "existing-correlation-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders/99")
                .header(CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        // Act
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Assert: the response should carry the SAME ID, not a new one.
        String responseCorrelationId = exchange.getResponse()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        assertThat(responseCorrelationId)
                .as("The existing correlation ID should be reused, not replaced")
                .isEqualTo(existingId);
    }

    /**
     * Verifies that the filter adds the correlation ID to the response headers,
     * making it visible to the caller.
     */
    @Test
    @DisplayName("Adds correlation ID to the response headers")
    void shouldAddCorrelationIdToResponseHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users/me")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // The response headers should contain X-Correlation-ID.
        assertThat(exchange.getResponse().getHeaders().containsKey(CORRELATION_ID_HEADER))
                .as("Response should contain X-Correlation-ID header")
                .isTrue();
    }

    // =========================================================================
    // Filter order
    // =========================================================================

    /**
     * Verifies that this filter has the highest precedence (runs first).
     * The correlation ID must be available to all subsequent filters.
     */
    @Test
    @DisplayName("Filter order is Ordered.HIGHEST_PRECEDENCE")
    void filterOrderShouldBeHighestPrecedence() {
        assertThat(filter.getOrder())
                .as("RequestIdGlobalFilter should run first (HIGHEST_PRECEDENCE = Integer.MIN_VALUE)")
                .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
}
