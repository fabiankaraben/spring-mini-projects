package com.example.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OAuth2 Authorization Server application.
 *
 * <p>This application acts as a standards-compliant OAuth2 / OIDC Authorization Server
 * built on top of Spring Authorization Server. It supports the following OAuth2 grant types:
 * <ul>
 *   <li><b>client_credentials</b> — machine-to-machine authentication where a trusted
 *       client authenticates itself using its client ID and secret to obtain an access token.</li>
 *   <li><b>authorization_code</b> — the recommended flow for user-facing applications;
 *       the user authenticates via a login form and the server issues an authorization code
 *       that the client exchanges for tokens.</li>
 *   <li><b>refresh_token</b> — allows a client to obtain a new access token without
 *       requiring the user to re-authenticate, using a previously issued refresh token.</li>
 * </ul>
 *
 * <p><b>Key features:</b>
 * <ul>
 *   <li>RSA-signed JWTs — the server generates an RSA key pair at startup (or loads it from
 *       configuration) and signs all JWTs with the private key. Consumers verify tokens using
 *       the public key exposed at {@code /oauth2/jwks}.</li>
 *   <li>Custom JWT claims — a {@code OAuth2TokenCustomizer} enriches every issued JWT with
 *       additional claims: user roles, tenant identifier, and a custom metadata map.</li>
 *   <li>JDBC client persistence — {@code RegisteredClient} entries are persisted to PostgreSQL
 *       using {@code JdbcRegisteredClientRepository}, so client registrations survive restarts.</li>
 *   <li>Token introspection — the {@code /oauth2/introspect} endpoint allows resource servers
 *       to validate opaque or JWT tokens.</li>
 *   <li>Token revocation — the {@code /oauth2/revoke} endpoint allows clients to revoke
 *       access tokens and refresh tokens.</li>
 * </ul>
 *
 * <p><b>Standard OAuth2 endpoints provided by Spring Authorization Server:</b>
 * <ul>
 *   <li>{@code POST /oauth2/token}        — issue access tokens</li>
 *   <li>{@code GET  /oauth2/authorize}    — authorization endpoint (authorization_code flow)</li>
 *   <li>{@code GET  /oauth2/jwks}         — JSON Web Key Set (public RSA keys for JWT verification)</li>
 *   <li>{@code POST /oauth2/introspect}   — token introspection (RFC 7662)</li>
 *   <li>{@code POST /oauth2/revoke}       — token revocation (RFC 7009)</li>
 *   <li>{@code GET  /.well-known/oauth-authorization-server} — discovery document</li>
 *   <li>{@code GET  /.well-known/openid-configuration}       — OIDC discovery document</li>
 * </ul>
 *
 * <p><b>Custom management endpoints:</b>
 * <ul>
 *   <li>{@code GET /auth/status}  — returns server info (issuer URI, active algorithms)</li>
 *   <li>{@code GET /auth/clients} — lists registered client IDs (admin use)</li>
 * </ul>
 */
@SpringBootApplication
public class AuthorizationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthorizationServerApplication.class, args);
    }
}
