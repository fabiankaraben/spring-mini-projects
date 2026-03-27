package com.example.keycloakidentity.controller;

import com.example.keycloakidentity.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller for publicly accessible endpoints (no authentication required).
 *
 * <p>These endpoints are intentionally open — they do not require a Keycloak token.
 * They serve two purposes:
 * <ol>
 *   <li>Infrastructure probes: load balancers and Docker Compose healthchecks call
 *       {@code /actuator/health} to verify the service is alive (configured in Actuator,
 *       not here).</li>
 *   <li>API discovery: the {@code /api/public/info} endpoint tells API consumers
 *       what this service does, how to authenticate with Keycloak, and what endpoints
 *       are available — before they obtain a token.</li>
 * </ol>
 *
 * <p>These endpoints are whitelisted in {@link com.example.keycloakidentity.config.SecurityConfig}
 * via {@code .requestMatchers("/api/public/**").permitAll()}.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    /**
     * The Keycloak realm name, injected from application configuration.
     * Used in the info endpoint to show the Keycloak authentication URL.
     */
    @Value("${keycloak.realm:demo-realm}")
    private String keycloakRealm;

    /**
     * The Keycloak server URL, injected from application configuration.
     * Used in the info endpoint to show the Keycloak server location.
     */
    @Value("${keycloak.server-url:http://localhost:8080}")
    private String keycloakServerUrl;

    /**
     * The service used to provide live user statistics in the info response.
     */
    private final UserService userService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param userService the user service (for live user count in info endpoint)
     */
    public PublicController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Returns publicly accessible server and Keycloak configuration information.
     *
     * <p>This endpoint is the starting point for API consumers. It provides:
     * <ul>
     *   <li>Service identification and version</li>
     *   <li>Keycloak server URL and realm name (so clients know where to authenticate)</li>
     *   <li>The OIDC token endpoint URL to obtain a JWT</li>
     *   <li>A summary of available endpoints and required roles</li>
     *   <li>Live user count from the application database</li>
     * </ul>
     *
     * <p>Example response:
     * <pre>{@code
     * {
     *   "service": "Keycloak Identity — Users API",
     *   "version": "1.0.0",
     *   "timestamp": "2025-01-01T00:00:00Z",
     *   "userCount": 4,
     *   "keycloak": {
     *     "serverUrl": "http://localhost:8080",
     *     "realm": "demo-realm",
     *     "tokenEndpoint": "http://localhost:8080/realms/demo-realm/protocol/openid-connect/token",
     *     "jwksUri": "http://localhost:8080/realms/demo-realm/protocol/openid-connect/certs"
     *   },
     *   "requiredRoles": { ... },
     *   "endpoints": { ... }
     * }
     * }</pre>
     *
     * @return HTTP 200 with server and Keycloak configuration metadata
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        // LinkedHashMap preserves insertion order for a consistent JSON field order
        Map<String, Object> info = new LinkedHashMap<>();

        info.put("service", "Keycloak Identity — Users API");
        info.put("version", "1.0.0");
        info.put("description",
                "Spring Boot backend delegating authentication entirely to Keycloak SSO. " +
                "All protected endpoints require a valid Keycloak-issued JWT Bearer token.");
        // Current server timestamp in ISO-8601 UTC format
        info.put("timestamp", Instant.now().toString());
        // Live user count from the in-memory application database
        info.put("userCount", userService.getUserCount());

        // Keycloak connection details — useful for API consumers discovering the service
        Map<String, String> keycloakInfo = new LinkedHashMap<>();
        keycloakInfo.put("serverUrl", keycloakServerUrl);
        keycloakInfo.put("realm", keycloakRealm);
        // OIDC token endpoint — clients POST here to obtain a JWT access token
        keycloakInfo.put("tokenEndpoint",
                keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token");
        // JWKS endpoint — where this application fetches Keycloak's public keys to verify JWTs
        keycloakInfo.put("jwksUri",
                keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/certs");
        info.put("keycloak", keycloakInfo);

        // Describe required Keycloak realm roles per access level
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put("USER", "Can read user data (GET endpoints)");
        roles.put("ADMIN", "Can read and write user data (all endpoints)");
        info.put("requiredRoles", roles);

        // Map each protected endpoint to its required role for quick API reference
        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("GET /api/users", "USER or ADMIN");
        endpoints.put("GET /api/users/me", "Any authenticated user");
        endpoints.put("GET /api/users/{id}", "USER or ADMIN");
        endpoints.put("POST /api/users", "ADMIN");
        endpoints.put("PUT /api/users/{id}", "ADMIN");
        endpoints.put("DELETE /api/users/{id}", "ADMIN");
        endpoints.put("GET /api/public/info", "Public (no token required)");
        endpoints.put("GET /actuator/health", "Public (no token required)");
        info.put("endpoints", endpoints);

        return ResponseEntity.ok(info);
    }
}
