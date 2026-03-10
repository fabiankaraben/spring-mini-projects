package com.example.autoconfiguration.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for the greeting endpoint.
 *
 * <p>The {@code name} field is optional: when absent (null or blank), the
 * {@link com.example.autoconfiguration.starter.GreetingService} falls back to
 * the configured {@code greeting.default-name} value.
 *
 * <p>Uses Java 16+ record syntax — an immutable data carrier with compact
 * constructor, equals, hashCode, and toString generated automatically.
 *
 * @param name the name to greet; optional (max 100 characters)
 */
public record GreetingRequest(

        /**
         * The person's name to include in the greeting.
         * Null or blank values trigger the default name from configuration.
         * Maximum 100 characters to prevent abuse.
         */
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name
) {
}
