package com.example.autoconfiguration.dto;

import java.time.Instant;

/**
 * Response DTO returned by the greeting endpoints.
 *
 * <p>Carries the persisted greeting log data back to the caller. Using a DTO
 * (instead of exposing the {@link com.example.autoconfiguration.entity.GreetingLog}
 * entity directly) decouples the API contract from the persistence model — allowing
 * each to evolve independently.
 *
 * <p>Uses Java 16+ record syntax for concise, immutable data transfer.
 *
 * @param id        the database-assigned identifier of the greeting log
 * @param name      the name that was greeted
 * @param message   the full greeting message produced by {@link com.example.autoconfiguration.starter.GreetingService}
 * @param createdAt the UTC timestamp when the greeting was generated
 */
public record GreetingResponse(
        Long id,
        String name,
        String message,
        Instant createdAt
) {
}
