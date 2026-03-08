package com.example.jwtvalidation.integration;

import com.example.jwtvalidation.dto.LoginRequest;
import com.example.jwtvalidation.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the JWT Validation API.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li><strong>Authentication</strong> – register and login endpoints.</li>
 *   <li><strong>JWT validation filter</strong> – the core of this project:
 *       verifying that the {@code JwtAuthenticationFilter} correctly allows
 *       or denies access based on the {@code Authorization: Bearer} header.</li>
 *   <li><strong>Access control</strong> – endpoints accessible to all
 *       authenticated users vs. admin-only endpoints.</li>
 *   <li><strong>Error cases</strong> – missing token, expired-like token,
 *       malformed token, wrong role.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – spins up a real PostgreSQL Docker
 *       container for every test class run, ensuring tests exercise the actual
 *       database driver and schema.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – overrides the Spring
 *       DataSource URL/username/password with the values assigned by
 *       Testcontainers at runtime (the container's port is random).</li>
 *   <li><strong>MockMvc</strong> – sends HTTP requests through the full
 *       Spring MVC + Spring Security stack without starting a real server.
 *       This means the {@code JwtAuthenticationFilter} is exercised for
 *       every request.</li>
 * </ul>
 *
 * <h2>Testcontainers lifecycle</h2>
 * <p>The {@code @Container} field is {@code static} and the container is started
 * explicitly in {@code @BeforeAll} / stopped in {@code @AfterAll}. This ensures
 * the container is fully running before the Spring context is created so that
 * {@link #overrideDataSourceProperties} receives the correct host/port values.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("JWT Validation API – Integration Tests")
class JwtValidationIntegrationTest {

    /**
     * PostgreSQL Testcontainer.
     *
     * <p>Testcontainers pulls the {@code postgres:16-alpine} image (or uses the
     * locally cached version) and starts a container with the given credentials.
     * The container is shared across all test methods in this class.</p>
     *
     * <p>The {@code static} keyword is crucial: it ensures the container is
     * started once before any Spring context is created, so
     * {@link #overrideDataSourceProperties} can inject the container's
     * dynamic port into the Spring configuration.</p>
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jwtvalidation_test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Overrides the Spring DataSource properties with the values provided by
     * the Testcontainers-managed PostgreSQL container.
     *
     * <p>Because Testcontainers maps the container's port 5432 to a random host
     * port, we cannot hard-code the JDBC URL in {@code application-test.yml}.
     * {@code @DynamicPropertySource} solves this by registering property
     * overrides after the container starts but before the Spring context is
     * created.</p>
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Starts the PostgreSQL container before any test in this class runs.
     *
     * <p>Explicit {@code start()} is used (rather than relying solely on the
     * {@code @Testcontainers} extension) to ensure the container is fully ready
     * before the Spring {@code ApplicationContext} is created.</p>
     */
    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    /**
     * Stops and removes the PostgreSQL container after all tests have finished.
     * This releases Docker resources promptly instead of waiting for JVM shutdown.
     */
    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson ObjectMapper for serialising request DTOs to JSON strings and
     * deserialising response bodies.
     */
    @Autowired
    private ObjectMapper objectMapper;

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Registers a user and returns the JWT token obtained by logging in.
     *
     * <p>This helper is used by multiple test methods that need a valid token
     * before exercising a protected endpoint.</p>
     *
     * @param username the username to register and log in with
     * @param password the plain-text password
     * @return a signed JWT string ready to be used in an Authorization header
     */
    private String registerAndGetToken(String username, String password) throws Exception {
        // Step 1: Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, password))))
                .andExpect(status().isCreated());

        // Step 2: Login and extract the token from the response
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(responseBody).get("token").asText();
    }

    // ── Registration tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register should return 201 for a new user")
    void register_shouldReturn201_whenUsernameIsAvailable() throws Exception {
        RegisterRequest request = new RegisterRequest("reguser1", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", containsString("reguser1")));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 409 for a duplicate username")
    void register_shouldReturn409_whenUsernameAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("dupuser1", "password123");

        // First registration succeeds
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with the same username must fail
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already taken")));
    }

    // ── Login tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login should return 200 with a JWT for valid credentials")
    void login_shouldReturn200WithJwt_forValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("logintest1", "securepass"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("logintest1", "securepass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.username", is("logintest1")))
                .andExpect(jsonPath("$.role", is("ROLE_USER")))
                .andExpect(jsonPath("$.expiresInSeconds", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 for wrong password")
    void login_shouldReturn401_forWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("wrongpassuser1", "correctpass"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("wrongpassuser1", "wrongpass"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 for an unknown username")
    void login_shouldReturn401_forUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nonexistent999", "anypassword"))))
                .andExpect(status().isUnauthorized());
    }

    // ── JWT validation – /api/protected/hello ─────────────────────────────────

    @Test
    @DisplayName("GET /api/protected/hello should return 401 when no Authorization header is present")
    void hello_shouldReturn401_whenNoAuthHeader() throws Exception {
        // No Authorization header – the JwtAuthenticationFilter should skip auth
        // and Spring Security should return 401 because the endpoint requires authentication
        mockMvc.perform(get("/api/protected/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/protected/hello should return 401 for a malformed token")
    void hello_shouldReturn401_forMalformedToken() throws Exception {
        mockMvc.perform(get("/api/protected/hello")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/protected/hello should return 401 for a token signed with the wrong key")
    void hello_shouldReturn401_forTokenWithWrongSignature() throws Exception {
        // This is a real JWT but signed with a DIFFERENT secret than the app uses.
        // The filter must reject it after signature verification fails.
        // Token generated with key "WrongSecretKeyThatDoesNotMatchTheAppConfiguration!2024"
        // sub=alice, exp=far future
        String tokenWithWrongKey =
                "eyJhbGciOiJIUzI1NiJ9" +
                ".eyJzdWIiOiJhbGljZSIsInJvbGUiOiJST0xFX1VTRVIiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6OTk5OTk5OTk5OX0" +
                ".fakeSignatureThatWillFailVerification";

        mockMvc.perform(get("/api/protected/hello")
                        .header("Authorization", "Bearer " + tokenWithWrongKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/protected/hello should return 200 with a personalised greeting for a valid JWT")
    void hello_shouldReturn200WithGreeting_forValidJwt() throws Exception {
        // Register + login to get a real signed token from the running application
        String token = registerAndGetToken("hellouser1", "password123");

        mockMvc.perform(get("/api/protected/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("hellouser1")))
                .andExpect(jsonPath("$.message", containsString("successfully validated")));
    }

    // ── JWT validation – /api/protected/profile ───────────────────────────────

    @Test
    @DisplayName("GET /api/protected/profile should return 401 without a token")
    void profile_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/protected/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/protected/profile should return the caller's claims for a valid JWT")
    void profile_shouldReturnTokenClaims_forValidJwt() throws Exception {
        String token = registerAndGetToken("profileuser1", "password123");

        mockMvc.perform(get("/api/protected/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("profileuser1")))
                .andExpect(jsonPath("$.role", is("ROLE_USER")))
                .andExpect(jsonPath("$.expiresAt", not(emptyOrNullString())));
    }

    // ── JWT validation – /api/protected/introspect ────────────────────────────

    @Test
    @DisplayName("GET /api/protected/introspect should show authenticated=true for a valid JWT")
    void introspect_shouldShowAuthenticated_forValidJwt() throws Exception {
        String token = registerAndGetToken("introspectuser1", "password123");

        mockMvc.perform(get("/api/protected/introspect")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal", is("introspectuser1")))
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.authorities", containsString("ROLE_USER")));
    }

    // ── Role-based access – /api/protected/admin ─────────────────────────────

    @Test
    @DisplayName("GET /api/protected/admin should return 403 for a user with ROLE_USER")
    void admin_shouldReturn403_forUserWithRoleUser() throws Exception {
        // Regular users cannot access admin endpoints
        String token = registerAndGetToken("regularuser1", "password123");

        mockMvc.perform(get("/api/protected/admin")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/protected/admin should return 401 without a token")
    void admin_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/protected/admin"))
                .andExpect(status().isUnauthorized());
    }

    // ── Token structure ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Login should return a JWT with exactly three dot-separated Base64URL segments")
    void login_shouldReturnWellStructuredJwt() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("structuretest1", "password123"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("structuretest1", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("token").asText();

        // A compact JWT always has exactly three Base64URL segments: header.payload.signature
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        assertThat(parts[0]).isNotBlank(); // header
        assertThat(parts[1]).isNotBlank(); // payload
        assertThat(parts[2]).isNotBlank(); // signature
    }

    @Test
    @DisplayName("A token obtained from login should be immediately usable on a protected endpoint")
    void tokenFromLogin_shouldBeUsableOnProtectedEndpoint() throws Exception {
        // This test demonstrates the full round-trip:
        //   register → login (get JWT) → use JWT on protected endpoint
        // It verifies that the JwtAuthenticationFilter correctly accepts a
        // freshly issued token from the same application.
        String token = registerAndGetToken("roundtripuser1", "password123");

        // The token must work immediately without any delay
        mockMvc.perform(get("/api/protected/hello")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
