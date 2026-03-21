package com.example.gatewaylimiting.model;

/**
 * Immutable value object representing the rate limit configuration for a single route.
 *
 * <p>This model is used by the {@code /gateway/rate-limits} endpoint to expose the
 * configured rate limit parameters for each route in a human-readable JSON format.
 * It is purely informational — it does not affect the actual rate limiting logic,
 * which is handled by the Redis Lua script executed by the {@code RequestRateLimiter}
 * filter.
 *
 * <p><b>Fields:</b>
 * <ul>
 *   <li>{@code routeId} — the unique identifier of the gateway route (e.g. "product-route").</li>
 *   <li>{@code path} — the path pattern this route matches (e.g. "/api/products/**").</li>
 *   <li>{@code replenishRate} — tokens added to the bucket per second (sustained throughput).</li>
 *   <li>{@code burstCapacity} — maximum tokens the bucket can hold (burst allowance).</li>
 *   <li>{@code keyResolverType} — describes how the rate limit key is resolved
 *       (e.g. "IP Address", "API Key Header").</li>
 * </ul>
 *
 * <p><b>Why a record?</b>
 * Java 16+ records are ideal for simple, immutable data carriers. The compiler
 * generates the constructor, getters, {@code equals}, {@code hashCode}, and
 * {@code toString} automatically, keeping the code concise.
 *
 * @param routeId         the unique route identifier
 * @param path            the URL path pattern this route matches
 * @param replenishRate   tokens added per second (average allowed request rate)
 * @param burstCapacity   maximum tokens in the bucket (peak burst allowance)
 * @param keyResolverType human-readable description of the key resolver strategy
 */
public record RateLimitInfo(
        String routeId,
        String path,
        int replenishRate,
        int burstCapacity,
        String keyResolverType
) {
}
