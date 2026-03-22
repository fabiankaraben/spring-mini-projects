package com.example.resourceserver.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;

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
 * Full integration tests for the OAuth2 Resource Server.
 *
 * <p><b>Testing strategy:</b>
 * These tests load the complete Spring Boot application context and send
 * HTTP requests through {@link MockMvc}. They verify:
 * <ol>
 *   <li>The Products API endpoints return correct data for authenticated requests.</li>
 *   <li>Unauthenticated requests are rejected with HTTP 401.</li>
 *   <li>Requests with insufficient scopes are rejected with HTTP 403.</li>
 *   <li>The public info endpoint is accessible without a token.</li>
 *   <li>The Actuator health endpoint reports UP.</li>
 *   <li>Full CRUD operations work end-to-end with appropriate scopes.</li>
 * </ol>
 *
 * <p><b>JWT simulation with Spring Security Test:</b>
 * Rather than generating real JWTs signed with an RSA key (which would require
 * a running Authorization Server during tests), we use Spring Security Test's
 * {@code jwt()} MockMvc post-processor. This injects a fake
 * {@link org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken}
 * directly into the security context — bypassing JWT signature verification.
 *
 * <p>The {@code jwt()} post-processor accepts custom authorities, claims, and subject,
 * making it easy to simulate any combination of scopes and roles without a real auth server.
 *
 * <p><b>WireMock for JWK Set:</b>
 * A WireMock container is started via Testcontainers to serve a stub JWK Set endpoint.
 * The application's {@code jwk-set-uri} is pointed at this container via
 * {@code @DynamicPropertySource}. This validates that the application can fetch and
 * cache JWK Set keys at startup — even though the actual JWT validation in tests
 * uses the {@code jwt()} post-processor shortcut.
 *
 * <p><b>Why WireMock here?</b>
 * Spring Boot's OAuth2 Resource Server auto-configuration eagerly validates the
 * {@code issuer-uri} (OIDC discovery) on context startup. By pointing the
 * {@code jwk-set-uri} directly (not via {@code issuer-uri}) to WireMock, we avoid
 * the need for OIDC discovery and keep the test setup simpler.
 *
 * <p><b>Container lifecycle:</b>
 * The WireMock container is static — started once per test class and shared
 * across all test methods. This is significantly faster than restarting it per test.
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @SpringBootTest} — loads the full application context.</li>
 *   <li>{@code @AutoConfigureMockMvc} — configures MockMvc without a real HTTP server.</li>
 *   <li>{@code @Testcontainers} — activates Testcontainers JUnit 5 extension.</li>
 *   <li>{@code @Container} — marks the static WireMock container field.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("OAuth2 Resource Server — integration tests")
class ResourceServerIntegrationTest {

    /**
     * WireMock container serving a stub JWK Set endpoint.
     *
     * <p>We use the multi-arch {@code wiremock/wiremock:3.9.2} image (NOT the
     * alpine variant, which is AMD64-only and fails on Apple Silicon / ARM64).
     *
     * <p>The stub mapping file is copied into the container at startup from the
     * test classpath. WireMock auto-loads all {@code *.json} files found in
     * {@code /home/wiremock/mappings/} on startup.
     *
     * <p>WireMock's default HTTP port is 8080 inside the container.
     * We wait for {@code /__admin/health} to confirm the container is ready.
     */
    @Container
    static final GenericContainer<?> wireMock =
            new GenericContainer<>("wiremock/wiremock:3.9.2")
                    .withExposedPorts(8080)
                    // Copy the JWK Set stub mapping file into the WireMock mappings directory.
                    // WireMock reads all JSON files in /home/wiremock/mappings/ at startup.
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("wiremock/mappings/jwks-stub.json"),
                            "/home/wiremock/mappings/jwks-stub.json")
                    // Wait for WireMock's admin health endpoint to confirm readiness.
                    // A 60-second timeout accommodates slow container startup on CI.
                    .waitingFor(
                            Wait.forHttp("/__admin/health")
                                    .forStatusCode(200)
                                    .withStartupTimeout(Duration.ofSeconds(60)));

    /**
     * Overrides the JWT JWK Set URI to point at the WireMock container.
     *
     * <p>This method is called by the JUnit 5 Spring extension AFTER all containers
     * have started (so the mapped port is known) but BEFORE the Spring context is
     * created. This ensures the resource server's JwtDecoder fetches the public key
     * from the WireMock stub rather than a real authorization server.
     *
     * <p>We also override {@code issuer-uri} to point at the WireMock container.
     * The issuer is validated in the JWT "iss" claim — our tests must use a
     * matching issuer in the jwt() post-processor.
     *
     * @param registry the Spring dynamic property registry
     */
    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        // Build the WireMock base URL using the dynamically assigned port
        String wireMockBaseUrl = "http://localhost:" + wireMock.getMappedPort(8080);

        // Override the JWK Set URI to point at the WireMock /oauth2/jwks stub
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> wireMockBaseUrl + "/oauth2/jwks");

        // Override the issuer URI. The resource server validates the "iss" claim in JWTs.
        // We set it to the WireMock base URL so our jwt() post-processor tests can use
        // a matching issuer, or simply not set the iss claim in tests that don't need it.
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> wireMockBaseUrl);
    }

    /**
     * MockMvc is auto-configured by @AutoConfigureMockMvc and wired here.
     * It provides a fluent DSL for making HTTP requests without a real HTTP server.
     */
    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // Public endpoints — no authentication required
    // =========================================================================

    @Nested
    @DisplayName("Public endpoints (no auth required)")
    class PublicEndpointTests {

        /**
         * Verifies that {@code GET /api/public/info} is accessible without any token
         * and returns the expected server info structure.
         */
        @Test
        @DisplayName("GET /api/public/info returns server info without authentication")
        void publicInfoEndpointIsAccessible() throws Exception {
            mockMvc.perform(get("/api/public/info"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.service").isNotEmpty())
                    .andExpect(jsonPath("$.version").value("1.0.0"))
                    .andExpect(jsonPath("$.productCount").isNumber())
                    .andExpect(jsonPath("$.availableScopes").isMap())
                    .andExpect(jsonPath("$.protectedEndpoints").isMap());
        }

        /**
         * Verifies that the Actuator health endpoint is accessible without authentication
         * and reports the application as UP.
         */
        @Test
        @DisplayName("GET /actuator/health returns UP without authentication")
        void actuatorHealthIsAccessible() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }
    }

    // =========================================================================
    // Unauthenticated requests — must return 401
    // =========================================================================

    @Nested
    @DisplayName("Unauthenticated requests → HTTP 401")
    class UnauthenticatedRequestTests {

        /**
         * Verifies that accessing a protected endpoint without any Bearer token
         * returns HTTP 401 Unauthorized.
         *
         * <p>Spring Security returns 401 (not 403) for missing credentials because
         * it cannot determine whether the request would be authorized — the caller
         * simply has not identified themselves yet.
         */
        @Test
        @DisplayName("GET /api/products without token returns HTTP 401")
        void getProductsWithoutTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/products"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Verifies that POST (write operation) without a token also returns 401.
         */
        @Test
        @DisplayName("POST /api/products without token returns HTTP 401")
        void postProductWithoutTokenReturns401() throws Exception {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"price\":10.0,\"category\":\"CAT\",\"stock\":5}"))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * Verifies that accessing a specific product by ID without a token returns 401.
         */
        @Test
        @DisplayName("GET /api/products/1 without token returns HTTP 401")
        void getProductByIdWithoutTokenReturns401() throws Exception {
            mockMvc.perform(get("/api/products/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Insufficient scope — must return 403
    // =========================================================================

    @Nested
    @DisplayName("Insufficient scope → HTTP 403")
    class InsufficientScopeTests {

        /**
         * Verifies that a token with only {@code products.read} scope cannot
         * POST (create) a product — HTTP 403 Forbidden.
         *
         * <p>The caller is authenticated (valid token) but not authorized (missing scope).
         * Spring Security returns 403 (not 401) because the identity is known but
         * the required permission is absent.
         */
        @Test
        @DisplayName("POST /api/products with only products.read scope returns HTTP 403")
        void postWithReadOnlyScopeReturns403() throws Exception {
            mockMvc.perform(post("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Test\",\"price\":10.99,\"category\":\"CAT\",\"stock\":5}"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Verifies that a token with only {@code products.write} scope cannot
         * GET (read) products — HTTP 403 Forbidden.
         */
        @Test
        @DisplayName("GET /api/products with only products.write scope returns HTTP 403")
        void getWithWriteOnlyScopeReturns403() throws Exception {
            mockMvc.perform(get("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write"))))
                    .andExpect(status().isForbidden());
        }

        /**
         * Verifies that a token with an unrelated scope cannot access product endpoints.
         */
        @Test
        @DisplayName("GET /api/products with unrelated scope returns HTTP 403")
        void getWithUnrelatedScopeReturns403() throws Exception {
            mockMvc.perform(get("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_other.scope"))))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // Products API — GET (read) operations
    // =========================================================================

    @Nested
    @DisplayName("Products API — GET (products.read scope)")
    class ProductReadTests {

        /**
         * Verifies that {@code GET /api/products} returns the list of all products
         * when the request has the {@code products.read} scope.
         *
         * <p>The pre-loaded sample products (6 products) are returned. We verify that
         * the response is a non-empty JSON array.
         */
        @Test
        @DisplayName("GET /api/products with products.read scope returns product list")
        void getProductsWithReadScopeReturnsProducts() throws Exception {
            mockMvc.perform(get("/api/products")
                            // jwt() injects a fake JwtAuthenticationToken into the security context.
                            // authorities() sets the GrantedAuthority objects — no real JWT needed.
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    // The pre-loaded sample data contains 6 products
                    .andExpect(jsonPath("$", hasSize(6)));
        }

        /**
         * Verifies that {@code GET /api/products?category=ELECTRONICS} filters
         * products by category correctly.
         */
        @Test
        @DisplayName("GET /api/products?category=ELECTRONICS returns only ELECTRONICS products")
        void getProductsByCategoryReturnsFilteredList() throws Exception {
            mockMvc.perform(get("/api/products")
                            .param("category", "ELECTRONICS")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    // Sample data has 2 ELECTRONICS products
                    .andExpect(jsonPath("$", hasSize(2)))
                    // All returned products must have category "ELECTRONICS"
                    .andExpect(jsonPath("$[0].category", is("ELECTRONICS")))
                    .andExpect(jsonPath("$[1].category", is("ELECTRONICS")));
        }

        /**
         * Verifies that {@code GET /api/products/{id}} returns the correct product
         * when the ID exists.
         */
        @Test
        @DisplayName("GET /api/products/1 returns the product with ID 1")
        void getProductByIdReturnsCorrectProduct() throws Exception {
            mockMvc.perform(get("/api/products/1")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"))))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.name", notNullValue()))
                    .andExpect(jsonPath("$.price", notNullValue()))
                    .andExpect(jsonPath("$.category", notNullValue()));
        }

        /**
         * Verifies that {@code GET /api/products/{id}} returns HTTP 404 for an ID
         * that does not exist in the repository.
         */
        @Test
        @DisplayName("GET /api/products/999 returns HTTP 404 for non-existent product")
        void getProductByNonExistentIdReturns404() throws Exception {
            mockMvc.perform(get("/api/products/999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"))))
                    .andExpect(status().isNotFound());
        }

        /**
         * Verifies that a token with both read and write scopes can still read products.
         * (Tests that having extra authorities does not break access.)
         */
        @Test
        @DisplayName("GET /api/products with both scopes also returns product list")
        void getProductsWithBothScopesReturnsProducts() throws Exception {
            mockMvc.perform(get("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"),
                                    new SimpleGrantedAuthority("SCOPE_products.write"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // =========================================================================
    // Products API — write operations (POST, PUT, DELETE)
    // =========================================================================

    @Nested
    @DisplayName("Products API — write operations (products.write scope)")
    class ProductWriteTests {

        /**
         * Verifies that {@code POST /api/products} with valid body and write scope
         * creates a product and returns HTTP 201 Created.
         *
         * <p>The response body should contain the newly created product with its
         * assigned ID and all the fields from the request.
         */
        @Test
        @DisplayName("POST /api/products with write scope creates product and returns 201")
        void createProductWithWriteScopeReturns201() throws Exception {
            String requestBody = """
                    {
                        "name": "Gaming Mouse",
                        "description": "Ergonomic gaming mouse with 16000 DPI",
                        "price": 59.99,
                        "category": "ELECTRONICS",
                        "stock": 100
                    }
                    """;

            mockMvc.perform(post("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.name", is("Gaming Mouse")))
                    .andExpect(jsonPath("$.category", is("ELECTRONICS")))
                    .andExpect(jsonPath("$.stock", is(100)));
        }

        /**
         * Verifies that {@code POST /api/products} with an invalid request body
         * (missing required fields) returns HTTP 400 Bad Request.
         *
         * <p>Bean Validation triggers automatically for {@code @Valid} annotated
         * request bodies. Missing required fields produce a 400 response.
         */
        @Test
        @DisplayName("POST /api/products with missing required fields returns HTTP 400")
        void createProductWithInvalidBodyReturns400() throws Exception {
            // Missing "price" and "category" — both are required
            String invalidBody = """
                    {
                        "name": "Incomplete Product"
                    }
                    """;

            mockMvc.perform(post("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Verifies that {@code PUT /api/products/{id}} updates an existing product
         * and returns HTTP 200 with the updated values.
         */
        @Test
        @DisplayName("PUT /api/products/1 with write scope updates product and returns 200")
        void updateProductWithWriteScopeReturns200() throws Exception {
            String updateBody = """
                    {
                        "name": "Updated Headphones",
                        "price": 179.99
                    }
                    """;

            mockMvc.perform(put("/api/products/1")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.name", is("Updated Headphones")));
        }

        /**
         * Verifies that {@code PUT /api/products/{id}} returns HTTP 404 when the
         * product ID does not exist.
         */
        @Test
        @DisplayName("PUT /api/products/999 returns HTTP 404 for non-existent product")
        void updateNonExistentProductReturns404() throws Exception {
            String updateBody = """
                    {
                        "name": "Does Not Exist"
                    }
                    """;

            mockMvc.perform(put("/api/products/999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody))
                    .andExpect(status().isNotFound());
        }

        /**
         * Verifies that {@code DELETE /api/products/{id}} with write scope deletes
         * the product and returns HTTP 204 No Content.
         */
        @Test
        @DisplayName("DELETE /api/products/2 with write scope deletes product and returns 204")
        void deleteProductWithWriteScopeReturns204() throws Exception {
            mockMvc.perform(delete("/api/products/2")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write"))))
                    .andExpect(status().isNoContent());
        }

        /**
         * Verifies that {@code DELETE /api/products/{id}} returns HTTP 404 when
         * the product does not exist.
         */
        @Test
        @DisplayName("DELETE /api/products/999 returns HTTP 404 for non-existent product")
        void deleteNonExistentProductReturns404() throws Exception {
            mockMvc.perform(delete("/api/products/999")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.write"))))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // Role-based access (custom "roles" claim)
    // =========================================================================

    @Nested
    @DisplayName("Role-based access via custom roles claim")
    class RoleBasedAccessTests {

        /**
         * Verifies that the ROLE_READER role (extracted from the custom "roles" claim)
         * does NOT grant access to the products endpoint, which requires SCOPE_products.read.
         *
         * <p>This test demonstrates that role-based and scope-based authorization are
         * independent. Having a ROLE_READER role is not sufficient to read products —
         * the specific SCOPE_products.read authority is required by the security config.
         */
        @Test
        @DisplayName("ROLE_READER alone does not grant GET /api/products (needs SCOPE_products.read)")
        void roleReaderAloneDoesNotGrantProductRead() throws Exception {
            // Token with only a ROLE_* authority (no SCOPE_products.read)
            mockMvc.perform(get("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("ROLE_READER"))))
                    .andExpect(status().isForbidden());
        }

        /**
         * Verifies that a token with both SCOPE_products.read and ROLE_READER
         * successfully grants access to the products list.
         *
         * <p>This simulates a token from the Authorization Server that has both
         * scope-based and role-based claims, as our custom token customizer adds.
         */
        @Test
        @DisplayName("SCOPE_products.read + ROLE_READER grants GET /api/products")
        void scopeAndRoleTogetherGrantProductRead() throws Exception {
            mockMvc.perform(get("/api/products")
                            .with(jwt().authorities(
                                    new SimpleGrantedAuthority("SCOPE_products.read"),
                                    new SimpleGrantedAuthority("ROLE_READER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
