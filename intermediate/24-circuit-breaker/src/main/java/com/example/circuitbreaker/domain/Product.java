package com.example.circuitbreaker.domain;

import java.math.BigDecimal;

/**
 * Immutable domain record representing a product returned by the upstream
 * inventory API.
 *
 * <p>Using a Java {@code record} instead of a regular class:
 * <ul>
 *   <li>Automatically generates constructor, getters ({@code id()}, {@code name()}, etc.),
 *       {@code equals()}, {@code hashCode()}, and {@code toString()}.</li>
 *   <li>Signals immutability — the circuit breaker's fallback response is a
 *       read-only snapshot, which makes the design intent clear.</li>
 * </ul>
 *
 * @param id          unique identifier assigned by the upstream service
 * @param name        human-readable product name
 * @param description short description of the product
 * @param price       current price of the product (from the upstream pricing feed)
 * @param available   whether the product is currently in stock
 */
public record Product(
        Integer id,
        String name,
        String description,
        BigDecimal price,
        Boolean available
) {
}
