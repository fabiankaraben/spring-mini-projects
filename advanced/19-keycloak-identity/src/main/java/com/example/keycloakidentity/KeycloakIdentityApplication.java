package com.example.keycloakidentity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Keycloak Identity application.
 *
 * <p>This application demonstrates how a Spring Boot backend can delegate user
 * authentication <em>entirely</em> to an external Keycloak SSO (Single Sign-On) server.
 * The backend itself never manages passwords or user accounts — Keycloak handles all
 * of that. The backend only validates the JWT tokens that Keycloak issues.
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *   [Client App]
 *       │
 *       │  1. Login (username + password)
 *       ▼
 *   [Keycloak SSO Server]
 *       │
 *       │  2. Issues JWT access token
 *       ▼
 *   [Client App]
 *       │
 *       │  3. API call with Authorization: Bearer <jwt>
 *       ▼
 *   [This Spring Boot Backend]
 *       │
 *       │  4. Validates JWT signature via Keycloak's JWKS URI
 *       │  5. Checks roles/scopes from JWT claims
 *       ▼
 *   [Protected Resource (e.g., Users API)]
 * </pre>
 *
 * <h2>How Keycloak JWT validation works in Spring Boot</h2>
 * <ol>
 *   <li>A client (browser, mobile app, or another service) authenticates with Keycloak
 *       using standard OIDC flows (password grant, authorization code, client credentials).</li>
 *   <li>Keycloak issues a signed JWT access token containing the user's identity,
 *       roles ({@code realm_access.roles}), and requested scopes.</li>
 *   <li>The client includes the JWT in every API request:
 *       {@code Authorization: Bearer <access_token>}.</li>
 *   <li>Spring Security's {@code BearerTokenAuthenticationFilter} intercepts the request
 *       and extracts the Bearer token.</li>
 *   <li>The {@code NimbusJwtDecoder} fetches Keycloak's RSA public keys from the
 *       JWKS URI ({@code /realms/{realm}/protocol/openid-connect/certs}) and verifies
 *       the JWT signature. The key set is cached and refreshed automatically.</li>
 *   <li>Our custom {@code KeycloakJwtAuthoritiesConverter} maps Keycloak-specific JWT
 *       claims ({@code realm_access.roles}, {@code resource_access.{client}.roles}) to
 *       Spring Security {@code GrantedAuthority} objects.</li>
 *   <li>The security filter chain enforces role-based access on each endpoint.</li>
 * </ol>
 *
 * <h2>Protected API endpoints (Users API)</h2>
 * <ul>
 *   <li>{@code GET  /api/users}        — list all users    (role: USER or ADMIN)</li>
 *   <li>{@code GET  /api/users/{id}}   — get user by ID   (role: USER or ADMIN)</li>
 *   <li>{@code POST /api/users}        — create a user     (role: ADMIN)</li>
 *   <li>{@code PUT  /api/users/{id}}   — update a user     (role: ADMIN)</li>
 *   <li>{@code DELETE /api/users/{id}} — delete a user     (role: ADMIN)</li>
 *   <li>{@code GET  /api/users/me}     — current user info (any authenticated)</li>
 * </ul>
 *
 * <h2>Public endpoints (no token required)</h2>
 * <ul>
 *   <li>{@code GET /actuator/health}   — health check for Docker probes</li>
 *   <li>{@code GET /api/public/info}   — server and Keycloak configuration info</li>
 * </ul>
 */
@SpringBootApplication
public class KeycloakIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeycloakIdentityApplication.class, args);
    }
}
