package com.example.gatewaylimiting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Gateway Rate Limiting mini-project.
 *
 * <p><b>What this application does:</b>
 * This Spring Boot application configures an API Gateway using Spring Cloud Gateway
 * with Redis-backed rate limiting. Every incoming HTTP request passes through a
 * rate limiting filter before being forwarded to the appropriate downstream service.
 *
 * <p><b>Rate limiting strategy — Token Bucket algorithm:</b>
 * The {@code RequestRateLimiter} filter in Spring Cloud Gateway uses the
 * <em>Token Bucket</em> algorithm implemented as a Lua script executed atomically
 * on Redis. Each client (identified by IP address or an API key header) receives
 * a virtual "bucket" of tokens:
 * <ul>
 *   <li><b>replenishRate</b> — tokens added per second (steady-state throughput).</li>
 *   <li><b>burstCapacity</b> — maximum tokens the bucket can hold (burst allowance).</li>
 * </ul>
 * Each request costs one token. When the bucket empties, the gateway returns
 * HTTP 429 Too Many Requests until tokens are replenished.
 *
 * <p><b>Why Redis?</b>
 * Storing the token bucket state in Redis allows multiple gateway instances to
 * share the same counters, making the rate limit accurate in a horizontally scaled
 * environment. Without Redis, each gateway pod would have its own counters, and a
 * client could bypass the limit by distributing requests across pods.
 *
 * <p><b>Key resolvers:</b>
 * A {@link org.springframework.cloud.gateway.filter.ratelimit.KeyResolver} maps each
 * incoming request to a string key that identifies the "bucket" to use. Two
 * resolvers are configured in this project:
 * <ul>
 *   <li>{@code ipKeyResolver} — uses the client's remote IP address.</li>
 *   <li>{@code apiKeyResolver} — uses the value of the {@code X-API-Key} header.</li>
 * </ul>
 *
 * <p><b>Routes defined:</b>
 * <pre>
 *   /api/products/** → product-service  (rate limit: 5 req/s, burst 10)
 *   /api/orders/**   → order-service    (rate limit: 3 req/s, burst 5)
 *   /api/users/**    → user-service     (rate limit: 10 req/s, burst 20)
 * </pre>
 */
@SpringBootApplication
public class GatewayRateLimitingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayRateLimitingApplication.class, args);
    }
}
