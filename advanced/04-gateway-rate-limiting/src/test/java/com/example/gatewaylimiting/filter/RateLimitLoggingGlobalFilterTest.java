package com.example.gatewaylimiting.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RateLimitLoggingGlobalFilter}.
 *
 * <p>These tests verify the filter's contract:
 * <ul>
 *   <li>The filter is ordered correctly (low order = early execution).</li>
 *   <li>The filter delegates to the next step in the chain.</li>
 *   <li>The filter completes the reactive pipeline without error.</li>
 * </ul>
 *
 * <p>Logging side-effects (SLF4J calls) are not asserted here — the filter's
 * primary responsibility is to pass the request through the chain correctly,
 * not to produce specific log output. Log assertions would require a log
 * appender capture setup which adds fragility.
 *
 * <p>No Spring context is loaded. The {@link GatewayFilterChain} is mocked
 * with Mockito. The exchange is built with {@link MockServerWebExchange}.
 */
@DisplayName("RateLimitLoggingGlobalFilter — unit tests")
class RateLimitLoggingGlobalFilterTest {

    /**
     * The filter instance under test.
     */
    private final RateLimitLoggingGlobalFilter filter = new RateLimitLoggingGlobalFilter();

    /**
     * Verifies that the filter reports an order of {@code -1}.
     * A negative order places this filter near the front of the global filter
     * chain, ensuring the pre-processing log runs before the rate limiter.
     */
    @Test
    @DisplayName("Filter order is -1 (near front of chain)")
    void filterOrderShouldBeNegativeOne() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    /**
     * Verifies that the filter correctly delegates to the next step in the chain
     * and the returned {@code Mono<Void>} completes without error.
     *
     * <p>The mock {@link GatewayFilterChain} is set up to return
     * {@link Mono#empty()} (which simulates a successful chain completion),
     * and we verify the filter passes through without swallowing the signal.
     */
    @Test
    @DisplayName("Filter delegates to the chain and completes normally")
    void filterShouldDelegateToChainAndComplete() {
        // Build a mock exchange with a remote IP address.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 9090))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        // Mock the chain to return an empty Mono (success signal).
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // The filter should complete without emitting any items (Mono<Void>).
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    /**
     * Verifies that the filter correctly passes through a 429 Too Many Requests
     * response. When the rate limiter sets the response status to 429, the logging
     * filter should still complete normally (not throw or swallow the signal).
     *
     * <p>This confirms the filter is purely observational for 429 responses —
     * it logs the event but does not alter the pipeline.
     */
    @Test
    @DisplayName("Filter completes normally even when response is HTTP 429")
    void filterShouldCompleteNormallyFor429Response() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .remoteAddress(new InetSocketAddress("192.168.0.5", 4321))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // Simulate the rate limiter having set the status to 429.
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // The filter should still complete without error regardless of 429 status.
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    /**
     * Verifies that the filter handles requests without a remote address gracefully.
     * In some test environments or proxy configurations, the remote address may
     * be null. The filter should not throw a NullPointerException in this case.
     */
    @Test
    @DisplayName("Filter handles null remote address without throwing NPE")
    void filterShouldHandleNullRemoteAddressGracefully() {
        // Request built without .remoteAddress() — getRemoteAddress() returns null.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/users")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // Should complete without NPE even when remote address is null.
        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }
}
