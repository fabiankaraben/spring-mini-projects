package com.example.authserver.dto;

import java.util.List;

/**
 * Data Transfer Object representing the Authorization Server's current status.
 *
 * <p>Returned by {@code GET /auth/status}. This DTO is serialized to JSON by
 * Jackson automatically via Spring MVC's message converters.
 *
 * <p>Uses Java 16+ records for a concise, immutable value object.
 * Records automatically generate:
 * <ul>
 *   <li>A canonical constructor with all fields</li>
 *   <li>{@code equals()}, {@code hashCode()}, and {@code toString()} methods</li>
 *   <li>Public accessor methods for each component (e.g., {@code issuerUri()})</li>
 * </ul>
 *
 * <p>Jackson serializes records out-of-the-box — each record component becomes
 * a JSON key using the exact component name.
 *
 * @param issuerUri           the canonical issuer URI embedded in all JWTs
 * @param status              server health status (always "UP" if reachable)
 * @param supportedGrantTypes list of OAuth2 grant type values this server supports
 * @param signingAlgorithm    the JWT signing algorithm name (e.g., "RS256")
 * @param jwksUri             the URL of the public JWK Set endpoint
 */
public record ServerStatusResponse(
        String issuerUri,
        String status,
        List<String> supportedGrantTypes,
        String signingAlgorithm,
        String jwksUri
) {}
