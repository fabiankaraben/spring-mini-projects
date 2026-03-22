package com.example.authserver.dto;

import java.util.List;

/**
 * Data Transfer Object representing a registered OAuth2 client for public listing.
 *
 * <p>Returned by {@code GET /auth/clients}. Contains only non-sensitive fields:
 * the client ID and its granted authorization types. The client secret is never exposed.
 *
 * <p>Uses a Java record for concise, immutable representation.
 *
 * @param clientId   the unique identifier for the registered OAuth2 client
 * @param grantTypes the list of OAuth2 grant type values this client is allowed to use
 */
public record ClientInfoResponse(
        String clientId,
        List<String> grantTypes
) {}
