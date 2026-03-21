package com.example.gatewaylimiting.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;

/**
 * Unit tests for {@link RateLimiterConfig} key resolvers.
 *
 * <p>These tests verify that both {@link KeyResolver} beans resolve the expected
 * rate limit key from different types of requests. No Spring context is started —
 * we instantiate {@link RateLimiterConfig} directly and use Spring's test
 * utilities ({@link MockServerHttpRequest} and {@link MockServerWebExchange}) to
 * build fake {@link org.springframework.web.server.ServerWebExchange} instances.
 *
 * <p><b>Why MockServerHttpRequest / MockServerWebExchange?</b>
 * Spring WebFlux provides these mock implementations specifically for unit testing
 * reactive handlers and filters without starting a Netty server. They let us
 * control exactly what headers, remote addresses, and paths are present.
 *
 * <p><b>Why StepVerifier?</b>
 * The {@link KeyResolver#resolve} method returns a {@code Mono<String>}. We use
 * Project Reactor's {@link StepVerifier} to subscribe to the {@code Mono} and
 * assert on its emitted value(s) in a test-friendly, synchronous way.
 */
@DisplayName("RateLimiterConfig — unit tests")
class RateLimiterConfigTest {

    /**
     * The class under test, instantiated directly (no Spring context needed).
     */
    private RateLimiterConfig config;

    @BeforeEach
    void setUp() {
        // Create a fresh instance for each test to avoid state leakage.
        config = new RateLimiterConfig();
    }

    // =========================================================================
    // ipKeyResolver tests
    // =========================================================================

    /**
     * Verifies that {@code ipKeyResolver} extracts the remote IP address from
     * the request's {@link java.net.InetSocketAddress} and returns it as the
     * rate limit key.
     */
    @Test
    @DisplayName("ipKeyResolver resolves to the client's remote IP address")
    void ipKeyResolverShouldReturnRemoteIp() {
        // Build a mock HTTP request with a specific remote address.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                // Set the remote address to simulate a specific client IP.
                .remoteAddress(new InetSocketAddress("192.168.1.100", 12345))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.ipKeyResolver();

        // Resolve the key and verify it equals the remote IP.
        Mono<String> keyMono = resolver.resolve(exchange);
        StepVerifier.create(keyMono)
                .expectNext("192.168.1.100")
                .verifyComplete();
    }

    /**
     * Verifies that {@code ipKeyResolver} falls back to "unknown" when the
     * remote address is not set on the request (e.g. in tests or some proxy
     * configurations where the socket address is not available).
     */
    @Test
    @DisplayName("ipKeyResolver falls back to 'unknown' when remote address is null")
    void ipKeyResolverShouldFallBackToUnknownWhenNoRemoteAddress() {
        // Build a request without a remote address.
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                // No remoteAddress() call — getRemoteAddress() returns null.
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.ipKeyResolver();

        Mono<String> keyMono = resolver.resolve(exchange);
        StepVerifier.create(keyMono)
                .expectNext("unknown")
                .verifyComplete();
    }

    /**
     * Verifies that {@code ipKeyResolver} correctly handles the loopback address
     * (127.0.0.1), which is the typical remote address in local development and
     * Testcontainers-based integration tests.
     */
    @Test
    @DisplayName("ipKeyResolver handles loopback address (127.0.0.1)")
    void ipKeyResolverShouldHandleLoopbackAddress() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 55432))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.ipKeyResolver();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("127.0.0.1")
                .verifyComplete();
    }

    // =========================================================================
    // apiKeyResolver tests
    // =========================================================================

    /**
     * Verifies that {@code apiKeyResolver} extracts the {@code X-API-Key} header
     * value and returns it as the rate limit key.
     */
    @Test
    @DisplayName("apiKeyResolver resolves to the X-API-Key header value")
    void apiKeyResolverShouldReturnApiKeyHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                .header("X-API-Key", "my-secret-api-key-123")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.apiKeyResolver();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("my-secret-api-key-123")
                .verifyComplete();
    }

    /**
     * Verifies that {@code apiKeyResolver} falls back to "anonymous" when the
     * {@code X-API-Key} header is absent. This is the unauthenticated case —
     * all such callers share a single "anonymous" bucket.
     */
    @Test
    @DisplayName("apiKeyResolver falls back to 'anonymous' when X-API-Key header is absent")
    void apiKeyResolverShouldFallBackToAnonymousWhenHeaderMissing() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/products")
                // Deliberately not setting X-API-Key header.
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.apiKeyResolver();

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("anonymous")
                .verifyComplete();
    }

    /**
     * Verifies that {@code apiKeyResolver} returns different keys for different
     * API key header values, so distinct clients get separate rate limit buckets.
     */
    @Test
    @DisplayName("apiKeyResolver returns distinct keys for different API key values")
    void apiKeyResolverShouldReturnDistinctKeysForDifferentApiKeys() {
        KeyResolver resolver = config.apiKeyResolver();

        MockServerHttpRequest request1 = MockServerHttpRequest
                .get("/api/users")
                .header("X-API-Key", "client-A-key")
                .build();
        MockServerHttpRequest request2 = MockServerHttpRequest
                .get("/api/users")
                .header("X-API-Key", "client-B-key")
                .build();

        // Both should resolve, but to different keys.
        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(request1)))
                .expectNext("client-A-key")
                .verifyComplete();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(request2)))
                .expectNext("client-B-key")
                .verifyComplete();
    }

    /**
     * Verifies that {@code apiKeyResolver} handles an empty string header value.
     * An empty string is a valid (though unusual) API key — it should be used
     * as-is, not replaced with "anonymous". This prevents an edge case where a
     * misconfigured client could silently share the anonymous bucket.
     */
    @Test
    @DisplayName("apiKeyResolver uses empty string API key as the bucket key")
    void apiKeyResolverShouldUseEmptyStringApiKeyAsIs() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/orders")
                .header("X-API-Key", "")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        KeyResolver resolver = config.apiKeyResolver();

        // An empty header is present but empty — it should NOT fall back to "anonymous".
        // The header IS present (just empty), so the resolver returns the empty string.
        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("")
                .verifyComplete();
    }
}
