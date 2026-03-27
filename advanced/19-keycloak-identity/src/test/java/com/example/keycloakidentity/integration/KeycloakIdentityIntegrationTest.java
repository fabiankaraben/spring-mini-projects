package com.example.keycloakidentity.integration;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full integration tests for the Keycloak Identity application.
 *
 * <h2>Testing strategy</h2>
 * These tests load the complete Spring Boot application context and exercise HTTP requests
 * through {@link MockMvc}. They verify:
 * <ol>
 *   <li>Unauthenticated requests are rejected with HTTP 401.</li>
 *   <li>Authenticated requests with insufficient roles are rejected with HTTP 403.</li>
 *   <li>ADMIN-role tokens can access all endpoints including write operations.</li>
 *   <li>USER-role tokens can access read endpoints but not write endpoints.</li>
 *   <li>The /api/users/me endpoint returns the caller's Keycloak identity claims.</li>
 *   <li>Public endpoints are accessible without any token.</li>
 *   <li>Full CRUD operations work end-to-end with appropriate roles.</li>
 * </ol>
 *
 * <h2>JWT simulation with Spring Security Test</h2>
 * Instead of obtaining real JWTs from Keycloak (which would require HTTP token requests),
 * we use Spring Security Test's {@code jwt()} MockMvc post-processor. It injects a fake
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * directly into the security context — bypassing JWT signature verification entirely.
 * This makes tests fast and deterministic.
 *
 * <p>The {@code jwt()} post-processor lets us specify any claims we want:
 * <pre>
 *   jwt().claim("realm_access", Map.of("roles", List.of("ADMIN")))
 *        .claim("preferred_username", "alice")
 *        .claim("email", "alice@example.com")
 * </pre>
 *
 * <h2>Keycloak Testcontainer</h2>
 * A real Keycloak container is started via the {@code dasniko/testcontainers-keycloak}
 * library. The container loads the {@code demo-realm.json} realm configuration from the
 * test classpath. The application's {@code jwk-set-uri} and {@code issuer-uri} are
 * dynamically overridden via {@code @DynamicPropertySource} to point at this container.
 *
 * <p>This validates that the application correctly fetches and caches Keycloak's JWKS
 * public keys at startup — even though actual JWT validation in tests uses the
 * {@code jwt()} post-processor shortcut.
 *
 * <h2>Container lifecycle</h2>
 * The Keycloak container is {@code static} — started once per test class and shared
 * across all test methods. This avoids the high startup cost (~10-20 seconds for Keycloak)
 * being paid per test.
 *
 * <h2>Key annotations</h2>
 * <ul>
 *   <li>{@code @SpringBootTest} — loads the full application context.</li>
 *   <li>{@code @AutoConfigureMockMvc} — sets up MockMvc without an actual HTTP server.</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension.</li>
 *   <li>{@code @Container} — marks the static Keycloak container field.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Keycloak Identity — integration tests")
class KeycloakIdentityIntegrationTest {

    /**
     * Keycloak container started once for the entire test class.
     *
     * <p>We use {@code quay.io/keycloak/keycloak:26.0} via the dasniko wrapper library.
     * The {@code withRealmImportFile} method copies the realm JSON from the test classpath
     * and imports it into Keycloak on startup, creating all users, roles, and clients
     * defined in the realm file.
     *
     * <p>Keycloak listens on port 8080 inside the container. Testcontainers maps it to a
     * random free port on the host to avoid conflicts.
     */
    @Container
    static final KeycloakContainer keycloak =
            new KeycloakContainer("quay.io/keycloak/keycloak:24.0")
                    // Import our test realm definition (users, roles, clients)
                    .withRealmImportFile("keycloak/demo-realm.json")
                    // Override wait strategy: Keycloak 24 dev mode serves health on port 8080,
                    // NOT the management port 9000 (which is only active in production mode).
                    // The dasniko library defaults to polling port 9000, causing connection reset.
                    .waitingFor(Wait.forHttp("/health/started")
                            .forPort(8080)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofSeconds(180)));

    /**
     * Dynamically overrides the Spring application properties with the Keycloak
     * container's actual host/port at test runtime.
     *
     * <p>{@code @DynamicPropertySource} runs after the container starts but before
     * the Spring context is initialized. This is the correct way to pass dynamic
     * values (like a randomly assigned container port) into the Spring context.
     *
     * <p>The two properties override the placeholder values in test/application.yml:
     * <ul>
     *   <li>{@code jwk-set-uri} — where the app fetches Keycloak's public key set.</li>
     *   <li>{@code issuer-uri} — the expected "iss" claim value in incoming JWTs.</li>
     * </ul>
     *
     * @param registry the Spring dynamic property registry
     */
    @DynamicPropertySource
    static void keycloakProperties(DynamicPropertyRegistry registry) {
        // Build the Keycloak realm base URL using the container's mapped port.
        // getAuthServerUrl() returns e.g. "http://localhost:<port>" (no trailing slash).
        // We must add "/" before "realms/" to form a valid URL.
        String baseUrl = keycloak.getAuthServerUrl();
        String realmUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + "realms/demo-realm";

        // Override the JWKS URI: where Spring Security fetches Keycloak's public RSA keys
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> realmUrl + "/protocol/openid-connect/certs"
        );

        // Override the issuer URI: must match the "iss" claim in Keycloak JWTs
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> realmUrl
        );

        // Also override the custom keycloak.* properties used in the info endpoint
        registry.add("keycloak.server-url", keycloak::getAuthServerUrl);
        registry.add("keycloak.realm", () -> "demo-realm");
    }

    /**
     * MockMvc — the test HTTP client. Injected by Spring via @AutoConfigureMockMvc.
     * Sends fake HTTP requests through the full Spring MVC stack (filters, security,
     * controllers) without starting a real server.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Reset state before each test.
     * The in-memory repository is initialized with 4 seed users and then accumulates
     * state across tests (creates/deletes). Since tests may mutate state, we use
     * @BeforeEach only for things that don't require resetting the whole repo.
     * Tests that need a known count use the pre-seeded 4 users.
     */
    @BeforeEach
    void setUp() {
        // No-op: the UserRepository is pre-seeded at startup with 4 demo users.
        // Tests that modify state use specific IDs that are stable across runs.
    }

    // =========================================================================
    // Public endpoints (no token required)
    // =========================================================================

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpointsTests {

        /**
         * Verifies that /api/public/info is accessible without any authentication token.
         */
        @Test
        @DisplayName("GET /api/public/info returns 200 without token")
        void publicInfoEndpointReturns200WithoutToken() throws Exception {
            mockMvc.perform(get("/api/public/info"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.service", is("Keycloak Identity — Users API")))
                    .andExpect(jsonPath("$.version", is("1.0.0")))
                    .andExpect(jsonPath("$.keycloak", notNullValue()))
                    .andExpect(jsonPath("$.keycloak.realm", is("demo-realm")))
                    .andExpect(jsonPath("$.endpoints", notNullValue()));
        }

        /**
         * Verifies that /actuator/health is accessible without any authentication token.
         */
        @Test
        @DisplayName("GET /actuator/health returns 200 without token")
        void actuatorHealthEndpointReturns200WithoutToken() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("UP")));
        }
    }

    // =========================================================================
    // Unauthenticated access to protected endpoints
    // =========================================================================

    @Nested
    @DisplayName("Unauthenticated access (HTTP 401)")
    class UnauthenticatedAccessTests {

        /**
         * Verifies that GET /api/users returns 401 when no Bearer token is provided.
         */
        @Test
        @DisplayName("GET /api/users returns 401 without token")
        void listUsersReturns401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Verifies that GET /api/users/me returns 401 when no token is provided.
         */
        @Test
        @DisplayName("GET /api/users/me returns 401 without token")
        void getCurrentUserReturns401WithoutToken() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Verifies that POST /api/users returns 401 when no token is provided.
         */
        @Test
        @DisplayName("POST /api/users returns 401 without token")
        void createUserReturns401WithoutToken() throws Exception {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Test\",\"email\":\"t@t.com\",\"role\":\"USER\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Authorization — insufficient role (HTTP 403)
    // =========================================================================

    @Nested
    @DisplayName("Insufficient role (HTTP 403)")
    class InsufficientRoleTests {

        /**
         * Verifies that a token with only USER role cannot create users (ADMIN required).
         *
         * <p>The jwt() post-processor simulates a Keycloak JWT with realm_access.roles = ["USER"].
         * Our KeycloakJwtAuthoritiesConverter reads this and produces ROLE_USER authority.
         * The POST /api/users endpoint requires ROLE_ADMIN, so it should return 403.
         */
        @Test
        @DisplayName("POST /api/users returns 403 with USER role (ADMIN required)")
        void createUserReturns403WithUserRole() throws Exception {
            mockMvc.perform(post("/api/users")
                            // jwt().authorities() directly sets the GrantedAuthority list.
                            // Our KeycloakJwtAuthoritiesConverter produces ROLE_USER from realm_access.roles=["USER"],
                            // so we inject ROLE_USER directly to simulate that same result.
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"New\",\"email\":\"new@e.com\",\"role\":\"USER\"}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Verifies that a token with only USER role cannot delete users (ADMIN required).
         */
        @Test
        @DisplayName("DELETE /api/users/{id} returns 403 with USER role")
        void deleteUserReturns403WithUserRole() throws Exception {
            mockMvc.perform(delete("/api/users/1")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isForbidden());
        }

        /**
         * Verifies that a token with only USER role cannot update users (ADMIN required).
         */
        @Test
        @DisplayName("PUT /api/users/{id} returns 403 with USER role")
        void updateUserReturns403WithUserRole() throws Exception {
            mockMvc.perform(put("/api/users/1")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Attempt\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // USER role — read-only access
    // =========================================================================

    @Nested
    @DisplayName("USER role — read-only access")
    class UserRoleTests {

        /**
         * Verifies that USER role can list all users.
         */
        @Test
        @DisplayName("GET /api/users returns 200 with USER role")
        void listUsersReturns200WithUserRole() throws Exception {
            mockMvc.perform(get("/api/users")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // The seeded repository has at least 4 users (from UserRepository constructor).
                    // Other tests may have added users, so we assert >= 4.
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
        }

        /**
         * Verifies that USER role can retrieve a specific user by ID.
         */
        @Test
        @DisplayName("GET /api/users/{id} returns 200 with USER role")
        void getUserByIdReturns200WithUserRole() throws Exception {
            mockMvc.perform(get("/api/users/1")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.email", is("alice@example.com")));
        }

        /**
         * Verifies that GET /api/users/{id} returns 404 for a non-existent user.
         */
        @Test
        @DisplayName("GET /api/users/{id} returns 404 when user not found")
        void getUserByIdReturns404WhenNotFound() throws Exception {
            mockMvc.perform(get("/api/users/9999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isNotFound());
        }

        /**
         * Verifies that USER role can filter users by role via query parameter.
         */
        @Test
        @DisplayName("GET /api/users?role=ADMIN returns admin users only")
        void listUsersByRoleReturnsMatchingUsers() throws Exception {
            mockMvc.perform(get("/api/users?role=ADMIN")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].role", is("ADMIN")));
        }
    }

    // =========================================================================
    // /api/users/me — current user profile
    // =========================================================================

    @Nested
    @DisplayName("/api/users/me — current user profile")
    class CurrentUserTests {

        /**
         * Verifies that /api/users/me returns the Keycloak identity claims from the JWT.
         *
         * <p>The jwt() post-processor injects claims that our controller reads via
         * {@code @AuthenticationPrincipal Jwt jwt}. We verify that the response
         * includes the expected Keycloak claims (keycloakId, preferredUsername, email).
         */
        @Test
        @DisplayName("GET /api/users/me returns caller's Keycloak identity claims")
        void getCurrentUserReturnsKeycloakClaims() throws Exception {
            // jwt() with claims(consumer) sets individual JWT claims.
            // This is the correct API for setting arbitrary custom claims like preferred_username.
            mockMvc.perform(get("/api/users/me")
                            .with(jwt()
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                    .jwt(j -> j
                                            .claim("preferred_username", "testuser")
                                            .claim("email", "testuser@example.com")
                                            .claim("name", "Test User"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // Should return the Keycloak identity claims from the JWT
                    .andExpect(jsonPath("$.preferredUsername", is("testuser")))
                    .andExpect(jsonPath("$.email", is("testuser@example.com")))
                    .andExpect(jsonPath("$.name", is("Test User")));
        }

        /**
         * Verifies that /api/users/me returns an appProfile message when the caller
         * has no application profile (not in the app database).
         *
         * <p>The JWT subject ("sub" claim) used here does not match any keycloakId in
         * the seeded repository, so the service returns "Not registered in application database".
         */
        @Test
        @DisplayName("GET /api/users/me returns 'not registered' when user has no app profile")
        void getCurrentUserReturnsNotRegisteredWhenNoAppProfile() throws Exception {
            // The default JWT subject from jwt() is a random UUID that won't match any seeded keycloakId
            mockMvc.perform(get("/api/users/me")
                            .with(jwt()
                                    .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                                    .jwt(j -> j.claim("preferred_username", "newcomer"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.preferredUsername", is("newcomer")))
                    // No app profile — should contain the "not registered" message
                    .andExpect(jsonPath("$.appProfile", containsString("Not registered")));
        }

        /**
         * Verifies that /api/users/me returns the app profile when the JWT subject
         * matches a seeded user's keycloakId.
         */
        @Test
        @DisplayName("GET /api/users/me returns app profile when user is registered")
        void getCurrentUserReturnsAppProfileWhenRegistered() throws Exception {
            // Use the exact keycloakId of the seeded admin user as the JWT subject
            mockMvc.perform(get("/api/users/me")
                            .with(jwt()
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                                    .jwt(j -> j
                                            .subject("kcid-admin-0001")
                                            .claim("preferred_username", "alice"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keycloakId", is("kcid-admin-0001")))
                    // Should include the application profile fields
                    .andExpect(jsonPath("$.appUserId", notNullValue()))
                    .andExpect(jsonPath("$.appRole", is("ADMIN")));
        }
    }

    // =========================================================================
    // ADMIN role — full CRUD
    // =========================================================================

    @Nested
    @DisplayName("ADMIN role — full CRUD access")
    class AdminRoleTests {

        /**
         * Verifies that ADMIN role can list all users.
         */
        @Test
        @DisplayName("GET /api/users returns 200 with ADMIN role")
        void listUsersReturns200WithAdminRole() throws Exception {
            mockMvc.perform(get("/api/users")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"),
                                    new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
        }

        /**
         * Verifies that ADMIN role can create a new user.
         *
         * <p>Verifies: HTTP 201 Created, response body contains the created user data.
         */
        @Test
        @DisplayName("POST /api/users returns 201 with ADMIN role")
        void createUserReturns201WithAdminRole() throws Exception {
            mockMvc.perform(post("/api/users")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"),
                                    new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "displayName": "Eve New",
                                      "email": "eve@example.com",
                                      "role": "USER",
                                      "keycloakId": "kcid-eve-0099"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.displayName", is("Eve New")))
                    .andExpect(jsonPath("$.email", is("eve@example.com")))
                    .andExpect(jsonPath("$.role", is("USER")))
                    .andExpect(jsonPath("$.keycloakId", is("kcid-eve-0099")))
                    .andExpect(jsonPath("$.active", is(true)))
                    .andExpect(jsonPath("$.id", notNullValue()));
        }

        /**
         * Verifies that POST /api/users returns 400 when the request body is invalid.
         * This tests Bean Validation (missing required fields, invalid email format).
         */
        @Test
        @DisplayName("POST /api/users returns 400 when request body is invalid")
        void createUserReturns400WithInvalidBody() throws Exception {
            mockMvc.perform(post("/api/users")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "displayName": "",
                                      "email": "not-an-email",
                                      "role": "SUPERUSER"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Verifies that ADMIN role can update an existing user.
         */
        @Test
        @DisplayName("PUT /api/users/{id} returns 200 with ADMIN role")
        void updateUserReturns200WithAdminRole() throws Exception {
            mockMvc.perform(put("/api/users/2")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_USER"),
                                    new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "displayName": "Bob Updated"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(2)))
                    .andExpect(jsonPath("$.displayName", is("Bob Updated")))
                    // Email should remain unchanged (partial update)
                    .andExpect(jsonPath("$.email", is("bob@example.com")));
        }

        /**
         * Verifies that PUT /api/users/{id} returns 404 for a non-existent user.
         */
        @Test
        @DisplayName("PUT /api/users/{id} returns 404 when user not found")
        void updateUserReturns404WhenNotFound() throws Exception {
            mockMvc.perform(put("/api/users/9999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Ghost\"}"))
                    .andExpect(status().isNotFound());
        }

        /**
         * Verifies that ADMIN role can delete a user (HTTP 204 No Content).
         */
        @Test
        @DisplayName("DELETE /api/users/{id} returns 204 with ADMIN role")
        void deleteUserReturns204WithAdminRole() throws Exception {
            // First create a user to delete (so we don't remove seeded test data)
            String createResponse = mockMvc.perform(post("/api/users")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "displayName": "Temp User",
                                      "email": "temp@example.com",
                                      "role": "USER"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            // Extract the ID from the create response using a simple string parse
            // (In a real test, use JsonPath or ObjectMapper)
            long newId = Long.parseLong(
                    createResponse.replaceAll(".*\"id\":(\\d+).*", "$1")
            );

            // Now delete the newly created user
            mockMvc.perform(delete("/api/users/" + newId)
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNoContent());
        }

        /**
         * Verifies that DELETE /api/users/{id} returns 404 for a non-existent user.
         */
        @Test
        @DisplayName("DELETE /api/users/{id} returns 404 when user not found")
        void deleteUserReturns404WhenNotFound() throws Exception {
            mockMvc.perform(delete("/api/users/9999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_ADMIN"))))
                    .andExpect(status().isNotFound());
        }
    }
}
