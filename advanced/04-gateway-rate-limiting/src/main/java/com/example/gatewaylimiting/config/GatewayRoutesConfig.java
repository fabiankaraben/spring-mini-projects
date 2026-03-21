package com.example.gatewaylimiting.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

/**
 * Defines all gateway routes using the Spring Cloud Gateway Java DSL,
 * each route protected by a Redis-backed rate limiting filter.
 *
 * <p><b>Rate Limiting Architecture:</b>
 * Every route in this gateway applies the {@code RequestRateLimiter} filter.
 * Under the hood, Spring Cloud Gateway uses the
 * {@link RedisRateLimiter} which:
 * <ol>
 *   <li>Generates a Redis key from the request using a {@link KeyResolver}.</li>
 *   <li>Runs a Lua script atomically on Redis implementing the
 *       <em>Token Bucket</em> algorithm.</li>
 *   <li>If tokens remain → decrements the bucket and allows the request through.</li>
 *   <li>If the bucket is empty → returns HTTP 429 Too Many Requests immediately,
 *       without forwarding the request to the downstream service.</li>
 * </ol>
 *
 * <p><b>IMPORTANT — RedisRateLimiter must be a Spring bean:</b>
 * {@link RedisRateLimiter} implements {@link org.springframework.beans.factory.InitializingBean}
 * and requires Spring to inject a {@code ReactiveStringRedisTemplate} via
 * {@code afterPropertiesSet()} before it can evaluate rate limits. If it is
 * instantiated with {@code new RedisRateLimiter(...)} directly inside the
 * route DSL lambda, Spring never calls that lifecycle callback and the filter
 * throws {@code IllegalStateException: RedisRateLimiter is not initialized} on
 * the first request.
 *
 * <p>The solution is to declare each {@link RedisRateLimiter} as a {@code @Bean}
 * in this same {@code @Configuration} class and inject those beans into the
 * {@link #gatewayRoutes} builder method. Spring then fully initialises each
 * limiter (template injected, Lua scripts loaded) before any route uses it.
 *
 * <p><b>Token Bucket parameters:</b>
 * <ul>
 *   <li><b>replenishRate</b> — number of tokens added to the bucket every second.
 *       This defines the average sustained throughput.</li>
 *   <li><b>burstCapacity</b> — maximum tokens the bucket can hold. A value higher
 *       than {@code replenishRate} allows short bursts above the sustained rate
 *       (e.g. 10 requests in the first second even if replenish rate is 5/s).</li>
 * </ul>
 *
 * <p><b>Routes defined:</b>
 * <pre>
 *   /api/products/**  →  product-service   (5 req/s, burst 10)
 *   /api/orders/**    →  order-service     (3 req/s, burst 5)
 *   /api/users/**     →  user-service      (10 req/s, burst 20)
 * </pre>
 *
 * <p><b>HTTP 429 response headers set by the rate limiter:</b>
 * <ul>
 *   <li>{@code X-RateLimit-Remaining} — tokens remaining in the bucket.</li>
 *   <li>{@code X-RateLimit-Burst-Capacity} — maximum bucket capacity.</li>
 *   <li>{@code X-RateLimit-Replenish-Rate} — tokens added per second.</li>
 *   <li>{@code X-RateLimit-Requested-Tokens} — tokens this request would cost.</li>
 * </ul>
 */
@Configuration
public class GatewayRoutesConfig {

    /**
     * Base URI of the downstream product microservice.
     * Defaults to {@code http://localhost:8081} for local development.
     * Override via the {@code services.product.url} property or the
     * {@code SERVICES_PRODUCT_URL} environment variable.
     */
    @Value("${services.product.url:http://localhost:8081}")
    private String productServiceUrl;

    /**
     * Base URI of the downstream order microservice.
     * Defaults to {@code http://localhost:8082}.
     */
    @Value("${services.order.url:http://localhost:8082}")
    private String orderServiceUrl;

    /**
     * Base URI of the downstream user microservice.
     * Defaults to {@code http://localhost:8083}.
     */
    @Value("${services.user.url:http://localhost:8083}")
    private String userServiceUrl;

    // =========================================================================
    // Single Spring-managed RedisRateLimiter bean with per-route config
    //
    // Spring Cloud Gateway's GatewayAutoConfiguration autowires a single
    // RateLimiter<?> bean into the RequestRateLimiterGatewayFilterFactory.
    // Declaring multiple RedisRateLimiter beans causes "expected single matching
    // bean but found N" UnsatisfiedDependencyException at startup.
    //
    // The correct pattern is ONE Spring-managed RedisRateLimiter bean configured
    // with per-route overrides via its routeConfig map. The map key is the route
    // ID (e.g. "product-route") and the value is a Config object that overrides
    // replenishRate and burstCapacity for that specific route.
    //
    // The constructor RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity,
    // defaultRequestedTokens) sets fallback values; the per-route map overrides
    // them when a request matches a specific route ID.
    // =========================================================================

    /**
     * The single Spring-managed {@link RedisRateLimiter} bean used by all routes.
     *
     * <p><b>Why a single bean?</b>
     * Spring Cloud Gateway's {@code RequestRateLimiterGatewayFilterFactory} is
     * autowired with exactly one {@code RateLimiter<?>} bean. Registering multiple
     * causes a {@code UnsatisfiedDependencyException} at startup.
     *
     * <p><b>Per-route limits via routeConfig:</b>
     * {@link RedisRateLimiter} supports per-route configuration through its
     * {@code routeConfig} map. Each entry maps a route ID to a
     * {@link RedisRateLimiter.Config} that overrides the default
     * {@code replenishRate} and {@code burstCapacity} for that route:
     * <ul>
     *   <li>{@code "product-route"} → 5 req/s, burst 10</li>
     *   <li>{@code "order-route"}   → 3 req/s, burst 5</li>
     *   <li>{@code "user-route"}    → 10 req/s, burst 20</li>
     * </ul>
     *
     * <p><b>Why this bean must be Spring-managed:</b>
     * {@link RedisRateLimiter} implements
     * {@link org.springframework.beans.factory.InitializingBean}.
     * Spring calls {@code afterPropertiesSet()} to inject the
     * {@code ReactiveStringRedisTemplate} required to run the Token Bucket
     * Lua script. Without this lifecycle callback, every request throws
     * {@code IllegalStateException: RedisRateLimiter is not initialized}.
     *
     * @return a fully initialised {@link RedisRateLimiter} with per-route configs
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        // Default fallback values (used for any route not in the config map).
        // replenishRate=5, burstCapacity=10, requestedTokens=1
        RedisRateLimiter limiter = new RedisRateLimiter(5, 10, 1);

        // Per-route overrides: populate the config map inherited from AbstractRateLimiter.
        // getConfig() returns a Map<String, Config>; the key MUST match the route ID
        // set in the gatewayRoutes() builder below. When a request arrives on a route,
        // RedisRateLimiter.loadConfiguration(routeId) looks up the map — if the routeId
        // is present, its Config overrides the default; otherwise the default is used.
        limiter.getConfig().put("product-route",
                // Product route: 5 req/s sustained, burst of 10
                new RedisRateLimiter.Config()
                        .setReplenishRate(5)
                        .setBurstCapacity(10)
                        .setRequestedTokens(1));
        limiter.getConfig().put("order-route",
                // Order route: stricter — 3 req/s sustained, burst of 5
                new RedisRateLimiter.Config()
                        .setReplenishRate(3)
                        .setBurstCapacity(5)
                        .setRequestedTokens(1));
        limiter.getConfig().put("user-route",
                // User route: permissive — 10 req/s sustained, burst of 20
                new RedisRateLimiter.Config()
                        .setReplenishRate(10)
                        .setBurstCapacity(20)
                        .setRequestedTokens(1));

        return limiter;
    }

    /**
     * Builds and registers all gateway routes, each protected by the shared
     * Spring-managed Redis rate limiter (with per-route overrides).
     *
     * <p>The {@code redisRateLimiter} bean is injected by Spring so it is fully
     * initialised (Redis template injected, Lua scripts loaded) before any route
     * uses it.
     *
     * @param builder          the route builder injected by Spring
     * @param ipKeyResolver    the key resolver that identifies clients by IP
     * @param redisRateLimiter the single Spring-managed rate limiter with per-route config
     * @return the fully configured {@link RouteLocator}
     */
    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder,
                                      KeyResolver ipKeyResolver,
                                      RedisRateLimiter redisRateLimiter) {
        return builder.routes()

                // ------------------------------------------------------------------
                // Route 1: Product Service
                //
                // Matches: /api/products/**
                // Rate limit: 5 req/s sustained, burst up to 10
                //   (set in redisRateLimiter routeConfig for "product-route").
                //
                // StripPrefix(1) removes "/api" so the downstream receives
                // "/products/..." instead of "/api/products/...".
                //
                // Example:
                //   Client:      GET /api/products
                //   Downstream:  GET /products  (at productServiceUrl)
                // ------------------------------------------------------------------
                .route("product-route", r -> r
                        .path("/api/products/**")
                        .filters(f -> f
                                // Remove the first path segment "/api" before forwarding.
                                .stripPrefix(1)
                                // Tell the downstream service where the request came from.
                                .addRequestHeader("X-Gateway-Source", "gateway-rate-limiting")
                                // Apply the Redis rate limiter. Per-route limits are looked up
                                // from the routeConfig map using the route ID "product-route".
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(ipKeyResolver)
                                        // Return HTTP 429 instead of the default HTTP 503.
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                                )
                                // Stamp the response so clients know which service handled it.
                                .addResponseHeader("X-Served-By", "product-service")
                        )
                        .uri(productServiceUrl)
                )

                // ------------------------------------------------------------------
                // Route 2: Order Service
                //
                // Matches: /api/orders/**
                // Rate limit: 3 req/s sustained, burst up to 5
                //   (set in redisRateLimiter routeConfig for "order-route").
                //
                // Stricter limit than the product route, simulating an expensive
                // or sensitive write endpoint.
                //
                // Example:
                //   Client:      GET /api/orders
                //   Downstream:  GET /orders  (at orderServiceUrl)
                // ------------------------------------------------------------------
                .route("order-route", r -> r
                        .path("/api/orders/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Gateway-Source", "gateway-rate-limiting")
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(ipKeyResolver)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                                )
                                .addResponseHeader("X-Served-By", "order-service")
                        )
                        .uri(orderServiceUrl)
                )

                // ------------------------------------------------------------------
                // Route 3: User Service
                //
                // Matches: /api/users/**
                // Rate limit: 10 req/s sustained, burst up to 20
                //   (set in redisRateLimiter routeConfig for "user-route").
                //
                // Permissive limit for lightweight, read-heavy user lookups.
                //
                // Example:
                //   Client:      GET /api/users
                //   Downstream:  GET /users  (at userServiceUrl)
                // ------------------------------------------------------------------
                .route("user-route", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Gateway-Source", "gateway-rate-limiting")
                                .requestRateLimiter(c -> c
                                        .setRateLimiter(redisRateLimiter)
                                        .setKeyResolver(ipKeyResolver)
                                        .setStatusCode(HttpStatus.TOO_MANY_REQUESTS)
                                )
                                .addResponseHeader("X-Served-By", "user-service")
                        )
                        .uri(userServiceUrl)
                )

                .build();
    }
}
