package com.example.gatewaylimiting.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Configuration class that defines the {@link KeyResolver} beans used by the
 * Spring Cloud Gateway {@code RequestRateLimiter} filter.
 *
 * <p><b>What is a KeyResolver?</b>
 * A {@code KeyResolver} maps an incoming request (represented as a
 * {@link org.springframework.web.server.ServerWebExchange}) to a string key.
 * The rate limiter uses this key to look up (or create) the client's token bucket
 * in Redis. Clients with the same key share the same bucket and therefore the
 * same rate limit counter.
 *
 * <p>Two resolvers are provided here:
 * <ol>
 *   <li>{@link #ipKeyResolver()} — identifies clients by their remote IP address.
 *       This is the default resolver ({@code @Primary}) used by routes that do not
 *       specify an explicit resolver bean name.</li>
 *   <li>{@link #apiKeyResolver()} — identifies clients by the value of the
 *       {@code X-API-Key} request header. If the header is absent the request is
 *       bucketed under the key {@code "anonymous"}, which effectively applies a
 *       shared, lower-priority rate limit to unauthenticated callers.</li>
 * </ol>
 *
 * <p><b>Reactive return type:</b>
 * Both resolvers return {@code Mono<String>} because Spring Cloud Gateway is fully
 * non-blocking. The key resolution runs on the Netty event loop and must not block.
 * {@code Mono.just(key)} wraps an already-available string in a no-op reactive
 * container without scheduling work on another thread.
 */
@Configuration
public class RateLimiterConfig {

    /**
     * IP-based key resolver.
     *
     * <p>Extracts the client's remote IP address from the request. This is the
     * simplest and most common rate limiting strategy — each unique IP address gets
     * its own token bucket.
     *
     * <p>This bean is marked {@code @Primary} so it is selected automatically by
     * routes that do not explicitly reference a resolver bean name. The
     * {@code RequestRateLimiter} filter's {@code key-resolver} property can still
     * override this on a per-route basis by naming the desired bean.
     *
     * <p><b>Important note for production:</b> if the gateway runs behind a reverse
     * proxy or load balancer, {@code getRemoteAddress()} may return the proxy's IP
     * rather than the real client's IP. In that case you should extract the client
     * IP from the {@code X-Forwarded-For} header instead.
     *
     * @return a {@link KeyResolver} that uses the remote IP address as the rate limit key
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // getRemoteAddress() returns the TCP-level socket address.
            // We unwrap the InetSocketAddress to get just the IP string.
            String ip = exchange.getRequest()
                    .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * API-key-based key resolver.
     *
     * <p>Reads the {@code X-API-Key} header from the incoming request and uses its
     * value as the rate limit key. This allows fine-grained per-client rate limits
     * when clients authenticate with an API key.
     *
     * <p>If the header is missing (unauthenticated requests), the key falls back to
     * {@code "anonymous"}. All anonymous callers share one bucket, which is typically
     * configured with a lower replenishRate to discourage unauthenticated use.
     *
     * <p>This bean is NOT {@code @Primary}, so it must be referenced explicitly in
     * route configuration via:
     * <pre>
     *   - name: RequestRateLimiter
     *     args:
     *       key-resolver: "#{@apiKeyResolver}"
     * </pre>
     *
     * @return a {@link KeyResolver} that uses the {@code X-API-Key} header value
     */
    @Bean
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            // First check for the X-API-Key header.
            String apiKey = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-API-Key");
            // Fall back to "anonymous" for unauthenticated requests.
            return Mono.just(apiKey != null ? apiKey : "anonymous");
        };
    }
}
