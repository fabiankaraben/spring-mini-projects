package com.example.authserver.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Base64;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the OAuth2 Authorization Server.
 *
 * <p><b>What is being tested here?</b>
 * These tests start a real PostgreSQL container via Testcontainers and
 * spin up the full Spring Boot application context. They then send real
 * HTTP requests through Spring's {@link MockMvc} to verify:
 * <ol>
 *   <li>The OAuth2 token endpoint ({@code POST /oauth2/token}) issues valid tokens
 *       for the {@code client_credentials} grant type.</li>
 *   <li>Issued JWT access tokens contain the expected standard claims
 *       ({@code iss}, {@code sub}, {@code exp}, {@code iat}).</li>
 *   <li>Issued JWT access tokens contain our custom claims
 *       ({@code roles}, {@code tenant}, {@code metadata}, {@code token_version}).</li>
 *   <li>The JWK Set endpoint ({@code GET /oauth2/jwks}) returns the RSA public keys.</li>
 *   <li>The OpenID Connect discovery endpoint returns the server's configuration.</li>
 *   <li>The custom management endpoints ({@code /auth/status}, {@code /auth/clients})
 *       respond with correct JSON.</li>
 *   <li>The Actuator health endpoint reports the server as UP.</li>
 *   <li>Protected endpoints require authentication (return 401).</li>
 *   <li>Token introspection works for issued tokens.</li>
 * </ol>
 *
 * <p><b>Container lifecycle:</b>
 * The PostgreSQL container is static — started once per test class and shared
 * across all test methods. This is faster than starting a new container per test.
 * Each test runs within the same database state (the schema is created once on
 * context startup, and the two demo clients are registered once).
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @SpringBootTest} — loads the full application context including
 *       all beans, security configuration, and database setup.</li>
 *   <li>{@code @AutoConfigureMockMvc} — configures MockMvc for sending HTTP requests
 *       without starting a real HTTP server (faster than RANDOM_PORT).</li>
 *   <li>{@code @Testcontainers} — activates Testcontainers JUnit 5 extension.</li>
 *   <li>{@code @Container} — marks the static PostgreSQL container field.</li>
 *   <li>{@code @DynamicPropertySource} — injects the container's JDBC URL into the
 *       Spring context before it starts.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("OAuth2 Authorization Server — integration tests")
class AuthorizationServerIntegrationTest {

    /**
     * PostgreSQL Testcontainer.
     *
     * <p>Uses the official {@code postgres:16-alpine} image — a small Alpine-based
     * image that supports both {@code linux/amd64} and {@code linux/arm64} (Apple Silicon).
     *
     * <p>Started once per test class (static + @Container), shared across all tests.
     * The Spring application context injects the JDBC URL via {@link #configureProperties}.
     */
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("authserver_test")
                    .withUsername("authuser")
                    .withPassword("authpass");

    /**
     * Overrides Spring DataSource properties with the Testcontainer connection details.
     *
     * <p>Called by the JUnit 5 Spring extension after all containers have started
     * (so their mapped ports are known) but BEFORE the Spring context is created.
     * This ensures the application connects to the Testcontainer PostgreSQL instance,
     * not the localhost PostgreSQL configured in application.yml.
     *
     * @param registry the Spring dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Override the DataSource URL to point at the Testcontainer instance.
        // postgres.getJdbcUrl() returns something like:
        //   jdbc:postgresql://localhost:55432/authserver_test
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * MockMvc is auto-configured by @AutoConfigureMockMvc and wired here.
     * It provides a DSL for sending HTTP requests to the application without
     * starting a real HTTP server.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Increases MockMvc's default timeout for requests.
     * Not strictly needed here (MockMvc is synchronous), but kept for clarity.
     */
    @BeforeEach
    void setUp() {
        // No per-test setup needed — the Spring context is loaded once for the class.
    }

    // =========================================================================
    // JWK Set endpoint
    // =========================================================================

    /**
     * Verifies that the JWK Set endpoint ({@code GET /oauth2/jwks}) returns HTTP 200
     * with a JSON body containing the RSA public key.
     *
     * <p>The JWK Set is used by resource servers to fetch the public key and verify
     * JWT signatures without contacting the authorization server on every request.
     * The response must contain a "keys" array with at least one RSA key (kty: RSA).
     */
    @Test
    @DisplayName("GET /oauth2/jwks returns the RSA public key set")
    void jwksEndpointReturnsRsaKeys() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty());
    }

    // =========================================================================
    // OpenID Connect discovery endpoint
    // =========================================================================

    /**
     * Verifies that the OIDC discovery endpoint returns the server's configuration.
     *
     * <p>{@code GET /.well-known/openid-configuration} is defined by the OIDC spec.
     * Clients use this document to auto-discover all endpoint URLs, supported scopes,
     * and token signing algorithms. The response must include the issuer and key endpoints.
     */
    @Test
    @DisplayName("GET /.well-known/openid-configuration returns OIDC discovery document")
    void oidcDiscoveryEndpointReturnsConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"))
                .andExpect(jsonPath("$.token_endpoint").isNotEmpty())
                .andExpect(jsonPath("$.jwks_uri").isNotEmpty())
                .andExpect(jsonPath("$.response_types_supported").isArray());
    }

    /**
     * Verifies that the OAuth2 Authorization Server metadata endpoint also works.
     *
     * <p>{@code GET /.well-known/oauth-authorization-server} is the RFC 8414 metadata
     * endpoint — the non-OIDC equivalent of the OIDC discovery document.
     */
    @Test
    @DisplayName("GET /.well-known/oauth-authorization-server returns AS metadata")
    void oauthAuthorizationServerMetadataEndpointReturnsData() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer").value("http://localhost:9000"));
    }

    // =========================================================================
    // Custom management endpoints
    // =========================================================================

    /**
     * Verifies that {@code GET /auth/status} returns the server status JSON.
     *
     * <p>The response must include the issuer URI, status "UP", and the
     * JWK Set URI.
     */
    @Test
    @DisplayName("GET /auth/status returns server status JSON")
    void authStatusEndpointReturnsServerInfo() throws Exception {
        mockMvc.perform(get("/auth/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuerUri").value("http://localhost:9000"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.signingAlgorithm").value("RS256"))
                .andExpect(jsonPath("$.jwksUri").value("http://localhost:9000/oauth2/jwks"))
                .andExpect(jsonPath("$.supportedGrantTypes").isArray());
    }

    /**
     * Verifies that {@code GET /auth/clients} returns a list of registered clients.
     *
     * <p>The two demo clients (messaging-client and service-account-client) are
     * registered at startup by {@code AuthorizationServerConfig}. The response
     * must list both of them.
     */
    @Test
    @DisplayName("GET /auth/clients returns both registered clients")
    void authClientsEndpointReturnsBothClients() throws Exception {
        mockMvc.perform(get("/auth/clients"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.clientId == 'messaging-client')]").exists())
                .andExpect(jsonPath("$[?(@.clientId == 'service-account-client')]").exists());
    }

    // =========================================================================
    // Actuator health endpoint
    // =========================================================================

    /**
     * Verifies that the Spring Boot Actuator health endpoint reports the server as UP.
     *
     * <p>The health endpoint is used by Docker Compose and Kubernetes probes to
     * determine whether the server is ready to accept traffic.
     */
    @Test
    @DisplayName("GET /actuator/health reports status UP")
    void actuatorHealthReportsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // =========================================================================
    // OAuth2 token endpoint — client_credentials grant
    // =========================================================================

    /**
     * Verifies that the token endpoint issues a JWT access token for a valid
     * {@code client_credentials} request from {@code service-account-client}.
     *
     * <p>The request uses HTTP Basic Authentication (Authorization header with
     * Base64-encoded clientId:clientSecret). The response must contain:
     * <ul>
     *   <li>{@code access_token} — the JWT string</li>
     *   <li>{@code token_type}   — "Bearer"</li>
     *   <li>{@code expires_in}   — a positive integer (seconds until expiry)</li>
     * </ul>
     */
    @Test
    @DisplayName("POST /oauth2/token with client_credentials returns JWT access token")
    void tokenEndpointIssuesAccessTokenForClientCredentials() throws Exception {
        // Base64-encode "service-account-client:service-secret" for HTTP Basic Auth
        String credentials = Base64.getEncoder().encodeToString(
                "service-account-client:service-secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber());
    }

    /**
     * Verifies that a {@code client_credentials} request for the messaging-client
     * also succeeds and returns a JWT with the correct token_type.
     */
    @Test
    @DisplayName("POST /oauth2/token for messaging-client with client_credentials returns token")
    void tokenEndpointIssuesTokenForMessagingClient() throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                "messaging-client:messaging-secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    /**
     * Verifies that the JWT access token issued for service-account-client
     * contains the custom claims added by {@link com.example.authserver.security.CustomTokenCustomizer}.
     *
     * <p>We decode the JWT payload (the middle Base64 segment) and verify that:
     * <ul>
     *   <li>{@code roles}         — present and contains "ROLE_API_READER"</li>
     *   <li>{@code tenant}        — present and equals "internal" (service- prefix)</li>
     *   <li>{@code metadata}      — present</li>
     *   <li>{@code token_version} — present and equals "1.0"</li>
     * </ul>
     *
     * <p>We manually decode the JWT payload because MockMvc doesn't have built-in
     * JWT claim assertions.
     */
    @Test
    @DisplayName("Issued JWT contains custom claims: roles, tenant, metadata, token_version")
    void issuedJwtContainsCustomClaims() throws Exception {
        // Base64-encode credentials for HTTP Basic Auth
        String credentials = Base64.getEncoder().encodeToString(
                "service-account-client:service-secret".getBytes());

        // Issue a token and capture the response body
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the access_token string from the JSON response
        String responseBody = result.getResponse().getContentAsString();
        // Parse the access_token value from the JSON using simple string manipulation
        // (no need for a full JSON parser — we just extract the token value)
        String accessToken = extractFieldFromJson(responseBody, "access_token");
        assertThat(accessToken).isNotBlank();

        // A JWT has three Base64URL-encoded parts separated by dots: header.payload.signature
        String[] jwtParts = accessToken.split("\\.");
        assertThat(jwtParts).hasSize(3);

        // Decode the payload (second part). Base64URL encoding may omit padding — we add it back.
        String payloadBase64 = jwtParts[1];
        // Add Base64 padding if needed (Base64URL omits '=' padding)
        int paddingNeeded = (4 - payloadBase64.length() % 4) % 4;
        payloadBase64 = payloadBase64 + "=".repeat(paddingNeeded);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(payloadBase64));

        // Verify standard JWT claims
        assertThat(payload).contains("\"iss\"");           // issuer claim
        assertThat(payload).contains("\"sub\"");           // subject claim
        assertThat(payload).contains("\"exp\"");           // expiration claim
        assertThat(payload).contains("\"iat\"");           // issued-at claim

        // Verify custom claims added by CustomTokenCustomizer
        assertThat(payload).contains("\"roles\"");         // custom: roles list
        assertThat(payload).contains("ROLE_API_READER");   // role mapped from api.read scope
        assertThat(payload).contains("\"tenant\"");        // custom: tenant identifier
        assertThat(payload).contains("\"internal\"");      // service- prefix → internal tenant
        assertThat(payload).contains("\"metadata\"");      // custom: metadata map
        assertThat(payload).contains("\"token_version\""); // custom: schema version
        assertThat(payload).contains("\"1.0\"");           // version is 1.0
    }

    /**
     * Verifies that the JWT issued for messaging-client with "read" scope
     * contains "ROLE_READER" in the roles claim and "default" as the tenant.
     */
    @Test
    @DisplayName("JWT for messaging-client contains ROLE_READER and default tenant")
    void issuedJwtForMessagingClientContainsCorrectRoleAndTenant() throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                "messaging-client:messaging-secret".getBytes());

        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "read"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String accessToken = extractFieldFromJson(responseBody, "access_token");

        // Decode JWT payload
        String[] jwtParts = accessToken.split("\\.");
        String payloadBase64 = jwtParts[1];
        int paddingNeeded = (4 - payloadBase64.length() % 4) % 4;
        payloadBase64 = payloadBase64 + "=".repeat(paddingNeeded);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(payloadBase64));

        // messaging-client has "read" scope → ROLE_READER
        assertThat(payload).contains("ROLE_READER");
        // messaging-client does not start with "service-" → default tenant
        assertThat(payload).contains("\"default\"");
    }

    /**
     * Verifies that the token endpoint returns HTTP 401 for an invalid client secret.
     *
     * <p>Spring Authorization Server responds with 401 Unauthorized when the
     * client credentials are incorrect. This protects against brute-force attacks.
     */
    @Test
    @DisplayName("POST /oauth2/token with wrong secret returns HTTP 401")
    void tokenEndpointRejects401ForWrongSecret() throws Exception {
        String badCredentials = Base64.getEncoder().encodeToString(
                "service-account-client:WRONG-SECRET".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + badCredentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies that the token endpoint returns an error for an unknown client ID.
     */
    @Test
    @DisplayName("POST /oauth2/token with unknown clientId returns HTTP 401")
    void tokenEndpointRejectsUnknownClient() throws Exception {
        String badCredentials = Base64.getEncoder().encodeToString(
                "unknown-client:some-secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + badCredentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies that the token endpoint returns HTTP 400 for an invalid grant type.
     *
     * <p>Spring Authorization Server returns 400 Bad Request with an
     * {@code unsupported_grant_type} error when the requested grant type is
     * not in the OAuth2 specification.
     */
    @Test
    @DisplayName("POST /oauth2/token with unsupported grant_type returns HTTP 400")
    void tokenEndpointRejects400ForUnsupportedGrantType() throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                "service-account-client:service-secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "invalid_grant_type"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that the token introspection endpoint ({@code POST /oauth2/introspect})
     * returns an active:true response for a freshly issued token.
     *
     * <p>Token introspection (RFC 7662) allows a resource server to validate
     * a token by posting it to the authorization server's introspection endpoint.
     * Only registered confidential clients may call the introspection endpoint
     * (they must authenticate with their own credentials).
     */
    @Test
    @DisplayName("POST /oauth2/introspect returns active:true for a valid token")
    void introspectionEndpointReportsTokenAsActive() throws Exception {
        // Step 1: Issue a token for service-account-client
        String credentials = Base64.getEncoder().encodeToString(
                "service-account-client:service-secret".getBytes());

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andReturn();

        String tokenResponse = tokenResult.getResponse().getContentAsString();
        String accessToken = extractFieldFromJson(tokenResponse, "access_token");

        // Step 2: Introspect the token using the same client's credentials
        // The introspecting client must authenticate — Spring Authorization Server
        // only allows registered clients to call /oauth2/introspect.
        mockMvc.perform(post("/oauth2/introspect")
                        .header("Authorization", "Basic " + credentials)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.client_id").value("service-account-client"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    /**
     * Verifies that the login page is accessible (HTTP 200) without credentials.
     *
     * <p>The authorization_code flow requires users to authenticate via the login
     * page before granting authorization to a client. The login page must be
     * publicly accessible (no credentials required to view it).
     */
    @Test
    @DisplayName("GET /login returns HTTP 200 (login page is public)")
    void loginPageIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Extracts a string field value from a simple JSON object string.
     *
     * <p>This is a minimal JSON field extractor that avoids adding a JSON parsing
     * library just for tests. It finds the value of the given field name in a JSON
     * object like: {@code {"access_token":"eyJ...","token_type":"Bearer"}}.
     *
     * <p>Works only for string values (not arrays or nested objects). Sufficient
     * for extracting token strings from the OAuth2 token endpoint response.
     *
     * @param json      the JSON string (must be a flat JSON object)
     * @param fieldName the field name whose string value to extract
     * @return the extracted string value, or empty string if not found
     */
    private String extractFieldFromJson(String json, String fieldName) {
        // Pattern: "fieldName":"value" — extract the value between the quotes after the colon
        String searchKey = "\"" + fieldName + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return "";
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return "";
        }
        return json.substring(startIndex, endIndex);
    }
}
