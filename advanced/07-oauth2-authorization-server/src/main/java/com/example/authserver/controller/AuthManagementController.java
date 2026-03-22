package com.example.authserver.controller;

import com.example.authserver.dto.ClientInfoResponse;
import com.example.authserver.dto.ServerStatusResponse;
import com.example.authserver.service.ClientRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing custom management endpoints for the Authorization Server.
 *
 * <p>These endpoints are <b>not</b> part of the OAuth2 / OIDC specification.
 * They are custom additions for operational visibility:
 * <ul>
 *   <li>{@code GET /auth/status}  — returns a summary of the server configuration
 *       (issuer URI, supported grant types, signing algorithm).</li>
 *   <li>{@code GET /auth/clients} — lists all registered OAuth2 clients by their
 *       client IDs (for administrative inspection).</li>
 * </ul>
 *
 * <p>Both endpoints are public (configured in {@code AuthorizationServerConfig}),
 * meaning they do not require an access token. In a production deployment you would
 * secure them behind an admin role or restrict them to internal networks.
 *
 * <p>The {@code /auth/**} path prefix is chosen to avoid collisions with the
 * standard Spring Authorization Server paths ({@code /oauth2/**}, {@code /.well-known/**}).
 */
@RestController
@RequestMapping("/auth")
public class AuthManagementController {

    /**
     * Service that provides business logic for client registry operations.
     * Injected via constructor injection (preferred over field injection).
     */
    private final ClientRegistrationService clientRegistrationService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param clientRegistrationService the service for client registry queries
     */
    public AuthManagementController(ClientRegistrationService clientRegistrationService) {
        this.clientRegistrationService = clientRegistrationService;
    }

    /**
     * Returns the current status and configuration of this Authorization Server.
     *
     * <p>The response includes:
     * <ul>
     *   <li>{@code issuerUri}          — the canonical issuer URI embedded in all JWTs</li>
     *   <li>{@code status}             — always {@code "UP"} if this endpoint is reachable</li>
     *   <li>{@code supportedGrantTypes}— the list of OAuth2 grant types this server handles</li>
     *   <li>{@code signingAlgorithm}   — the JWT signing algorithm (RS256 in this demo)</li>
     *   <li>{@code jwksUri}            — the URL where the public signing keys can be fetched</li>
     * </ul>
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "issuerUri": "http://localhost:9000",
     *   "status": "UP",
     *   "supportedGrantTypes": ["client_credentials", "authorization_code", "refresh_token"],
     *   "signingAlgorithm": "RS256",
     *   "jwksUri": "http://localhost:9000/oauth2/jwks"
     * }
     * }</pre>
     *
     * @return HTTP 200 with a {@link ServerStatusResponse} body
     */
    @GetMapping("/status")
    public ResponseEntity<ServerStatusResponse> getStatus() {
        ServerStatusResponse response = new ServerStatusResponse(
                "http://localhost:9000",
                "UP",
                List.of("client_credentials", "authorization_code", "refresh_token"),
                "RS256",
                "http://localhost:9000/oauth2/jwks"
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a list of all registered OAuth2 clients on this server.
     *
     * <p>Only the client ID and grant types are exposed — never the client secret.
     * This endpoint is intended for administrators to verify which clients are registered
     * without having to query the database directly.
     *
     * <p>Example response:
     * <pre>{@code
     * [
     *   { "clientId": "messaging-client",       "grantTypes": ["authorization_code", "client_credentials", "refresh_token"] },
     *   { "clientId": "service-account-client", "grantTypes": ["client_credentials"] }
     * ]
     * }</pre>
     *
     * @return HTTP 200 with a list of {@link ClientInfoResponse} objects
     */
    @GetMapping("/clients")
    public ResponseEntity<List<ClientInfoResponse>> listClients() {
        List<ClientInfoResponse> clients = clientRegistrationService.listRegisteredClients();
        return ResponseEntity.ok(clients);
    }
}
