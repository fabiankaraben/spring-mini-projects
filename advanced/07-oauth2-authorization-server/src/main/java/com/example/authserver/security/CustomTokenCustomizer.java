package com.example.authserver.security;

import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom JWT token customizer that enriches every issued JWT access token
 * with additional non-standard claims beyond the standard OAuth2 claims.
 *
 * <p>Spring Authorization Server calls this customizer just before encoding a JWT.
 * The {@link JwtEncodingContext} provides access to:
 * <ul>
 *   <li>The JWT claims builder (to add/remove claims)</li>
 *   <li>The token type (access token, ID token, refresh token)</li>
 *   <li>The registered client that requested the token</li>
 *   <li>The principal (authenticated user or client)</li>
 *   <li>The authorized scopes</li>
 * </ul>
 *
 * <p><b>Custom claims added to access tokens:</b>
 * <ul>
 *   <li>{@code roles}    — a list of role strings derived from the granted scopes.
 *       For example, the {@code write} scope maps to {@code ROLE_WRITER}.</li>
 *   <li>{@code tenant}   — the tenant identifier. In a multi-tenant system each
 *       client belongs to a tenant. Here we derive it from the clientId prefix
 *       or fall back to a default value.</li>
 *   <li>{@code metadata} — a map of arbitrary key/value pairs. Downstream services
 *       can use these to make authorization decisions without calling the auth server.</li>
 *   <li>{@code token_version} — a version string for the custom claims schema,
 *       allowing downstream services to handle future claim changes gracefully.</li>
 * </ul>
 *
 * <p><b>Why enrich JWTs?</b>
 * JWTs are self-contained tokens. By embedding authorization data (roles, tenant) directly
 * in the token, resource servers can make access control decisions locally without round-trips
 * to the authorization server. This reduces latency and eliminates the auth server as a
 * runtime dependency for authorization checks.
 *
 * <p><b>Security consideration:</b>
 * Keep JWTs small — a large token increases HTTP header sizes and may hit limits in some
 * reverse proxies. Only embed claims that are frequently needed by many services.
 * For rarely-needed or sensitive data, prefer token introspection.
 */
public class CustomTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /**
     * Enriches the JWT claims for access tokens.
     *
     * <p>This method is called once per token issuance. For token types other than
     * ACCESS_TOKEN, no additional claims are added (ID tokens follow OIDC spec claims).
     *
     * @param context the JWT encoding context provided by Spring Authorization Server
     */
    @Override
    public void customize(JwtEncodingContext context) {
        // Only enrich ACCESS_TOKEN JWTs.
        // ID tokens (issued in OIDC flows) follow the OIDC spec and should not
        // receive arbitrary extra claims unless specifically needed.
        if (!context.getTokenType().getValue().equals("access_token")) {
            return;
        }

        // Retrieve the set of scopes that were granted to this token.
        // Scopes are determined at the time of authorization and may be a
        // subset of the client's registered scopes (e.g., the user consented
        // only to "read" even though the client requested "read write").
        Set<String> scopes = context.getAuthorizedScopes();

        // --- Custom claim: roles ---
        // Map each granted scope to a ROLE_ string. Resource servers can use these
        // roles in @PreAuthorize or custom access decision logic.
        List<String> roles = mapScopesToRoles(scopes);
        context.getClaims().claim("roles", roles);

        // --- Custom claim: tenant ---
        // The tenant is derived from the registered client's clientId.
        // Convention: if the clientId starts with "service-", the tenant is "internal".
        // All other clients belong to the "default" tenant.
        // In a real system, this would be fetched from a tenant database.
        String clientId = context.getRegisteredClient().getClientId();
        String tenant = deriveTenant(clientId);
        context.getClaims().claim("tenant", tenant);

        // --- Custom claim: metadata ---
        // A flexible map of extra data that downstream services can use.
        // Examples: feature flags, rate limit tier, preferred locale.
        Map<String, Object> metadata = buildMetadata(clientId, scopes);
        context.getClaims().claim("metadata", metadata);

        // --- Custom claim: token_version ---
        // A version identifier for the custom claims schema.
        // When you introduce breaking changes to the claims structure,
        // increment this version so downstream services can adapt.
        context.getClaims().claim("token_version", "1.0");
    }

    /**
     * Maps a set of OAuth2 scopes to a list of role strings.
     *
     * <p>Role mapping convention used in this demo:
     * <ul>
     *   <li>{@code read}    → {@code ROLE_READER}</li>
     *   <li>{@code write}   → {@code ROLE_WRITER}</li>
     *   <li>{@code api.read}→ {@code ROLE_API_READER}</li>
     *   <li>{@code openid}  → (no role added — OIDC scope, not an authorization scope)</li>
     *   <li>{@code profile} → (no role added — OIDC profile scope)</li>
     * </ul>
     *
     * @param scopes the set of authorized scopes
     * @return a list of role strings (may be empty if no mappable scopes are present)
     */
    List<String> mapScopesToRoles(Set<String> scopes) {
        return scopes.stream()
                .filter(scope -> !scope.equals("openid") && !scope.equals("profile"))
                .map(scope -> switch (scope) {
                    case "read"     -> "ROLE_READER";
                    case "write"    -> "ROLE_WRITER";
                    case "api.read" -> "ROLE_API_READER";
                    // Unknown scopes are converted to ROLE_<SCOPE_UPPERCASE>
                    default         -> "ROLE_" + scope.toUpperCase().replace(".", "_");
                })
                .sorted() // sorted for deterministic output (easier to test)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Derives the tenant identifier from a client ID.
     *
     * <p>Convention: clients whose ID starts with {@code "service-"} are internal
     * service accounts belonging to the {@code "internal"} tenant.
     * All other clients belong to the {@code "default"} tenant.
     *
     * <p>In a production system, the tenant would be stored as client metadata and
     * looked up from the {@code RegisteredClient}'s client settings map.
     *
     * @param clientId the OAuth2 client identifier
     * @return the tenant string
     */
    String deriveTenant(String clientId) {
        if (clientId != null && clientId.startsWith("service-")) {
            return "internal";
        }
        return "default";
    }

    /**
     * Builds a metadata map embedded in the JWT for downstream service use.
     *
     * <p>The metadata map includes:
     * <ul>
     *   <li>{@code client_id}      — echoed back so downstream services don't need to
     *       look up the standard {@code client_id} claim separately.</li>
     *   <li>{@code rate_limit_tier}— a tier string used by API gateways for rate limiting.
     *       Service clients get "premium", others get "standard".</li>
     *   <li>{@code allowed_scopes} — the granted scopes as a comma-separated string.</li>
     * </ul>
     *
     * @param clientId the registered client ID
     * @param scopes   the set of authorized scopes
     * @return an immutable metadata map
     */
    Map<String, Object> buildMetadata(String clientId, Set<String> scopes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("client_id", clientId);
        // Service accounts get a higher rate-limit tier in this demo.
        metadata.put("rate_limit_tier", clientId != null && clientId.startsWith("service-")
                ? "premium" : "standard");
        metadata.put("allowed_scopes", String.join(",", scopes));
        return metadata;
    }
}
