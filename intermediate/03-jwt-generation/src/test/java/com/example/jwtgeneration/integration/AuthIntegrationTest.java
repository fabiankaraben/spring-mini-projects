package com.example.jwtgeneration.integration;

import com.example.jwtgeneration.dto.LoginRequest;
import com.example.jwtgeneration.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the authentication API.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>Registration ({@code POST /api/auth/register}) – happy path and
 *       duplicate username rejection.</li>
 *   <li>Login ({@code POST /api/auth/login}) – happy path (receives a JWT),
 *       wrong password, and unknown username.</li>
 *   <li>Token structure – that the returned JWT has three dot-separated
 *       Base64URL segments.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – spins up a real PostgreSQL Docker
 *       container for every test class run, ensuring tests exercise the actual
 *       database driver and schema.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – overrides the Spring
 *       DataSource URL/username/password with the values assigned by
 *       Testcontainers at runtime.</li>
 *   <li><strong>MockMvc</strong> – sends HTTP requests through the full
 *       Spring MVC stack (filters, controllers, serialisation) without
 *       starting a real server.</li>
 * </ul>
 *
 * <h2>Testcontainers lifecycle</h2>
 * The {@code @Container} field is {@code static} and the container is started
 * explicitly in {@code @BeforeAll} / stopped in {@code @AfterAll}. This ensures
 * the container is fully running before the Spring context is created and that
 * {@link #overrideDataSourceProperties} receives the correct host/port values.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Auth API – Integration Tests")
class AuthIntegrationTest {

    /**
     * PostgreSQL Testcontainer.
     *
     * <p>Testcontainers pulls the {@code postgres:16-alpine} image (or uses the
     * locally cached version) and starts a container with the given credentials.
     * The container is shared across all test methods in this class.
     *
     * <p>The {@code static} keyword is crucial: it ensures the container is
     * started once before any Spring context is created, so
     * {@link #overrideDataSourceProperties} can inject the container's
     * dynamic port into the Spring configuration.
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jwtdb_test")
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
     * created.
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
     * before the Spring {@code ApplicationContext} is created.
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
     * Jackson ObjectMapper for serialising request DTOs to JSON strings.
     */
    @Autowired
    private ObjectMapper objectMapper;

    // ── Registration tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register should return 201 and success message for a new user")
    void register_shouldReturn201_whenUsernameIsAvailable() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser1", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", containsString("newuser1")));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 409 when username already exists")
    void register_shouldReturn409_whenUsernameAlreadyExists() throws Exception {
        RegisterRequest request = new RegisterRequest("duplicateuser", "password123");

        // First registration should succeed
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same username must be rejected
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already taken")));
    }

    @Test
    @DisplayName("POST /api/auth/register should return 400 for blank username")
    void register_shouldReturn400_whenUsernameIsBlank() throws Exception {
        RegisterRequest request = new RegisterRequest("", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/register should return 400 for password shorter than 6 chars")
    void register_shouldReturn400_whenPasswordTooShort() throws Exception {
        RegisterRequest request = new RegisterRequest("validuser", "abc");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Login tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login should return 200 and a JWT for valid credentials")
    void login_shouldReturn200WithJwt_forValidCredentials() throws Exception {
        // Step 1: Register the user first
        RegisterRequest registerRequest = new RegisterRequest("loginuser1", "securepass");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Step 2: Login with the same credentials
        LoginRequest loginRequest = new LoginRequest("loginuser1", "securepass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                // Token must be present and non-blank
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                // Token type must always be "Bearer"
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                // Username in response must match what was registered
                .andExpect(jsonPath("$.username", is("loginuser1")))
                // New users always get ROLE_USER
                .andExpect(jsonPath("$.role", is("ROLE_USER")))
                // Expiry must be a positive number
                .andExpect(jsonPath("$.expiresInSeconds", greaterThan(0)));
    }

    @Test
    @DisplayName("POST /api/auth/login should return a JWT with three dot-separated segments")
    void login_shouldReturnWellStructuredJwt() throws Exception {
        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("jwtstructureuser", "password123"))))
                .andExpect(status().isCreated());

        // Login and capture the response
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jwtstructureuser", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        // Extract the raw token from the JSON response
        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        // A valid compact JWT has exactly three Base64URL segments separated by dots
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        // Each part must be non-blank
        assertThat(parts[0]).isNotBlank(); // header
        assertThat(parts[1]).isNotBlank(); // payload
        assertThat(parts[2]).isNotBlank(); // signature
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 for wrong password")
    void login_shouldReturn401_forWrongPassword() throws Exception {
        // Register with a known password
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("wrongpassuser", "correctpass"))))
                .andExpect(status().isCreated());

        // Try logging in with a different password
        LoginRequest loginRequest = new LoginRequest("wrongpassuser", "wrongpass");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login should return 401 for unknown username")
    void login_shouldReturn401_forUnknownUsername() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistentuser", "anypassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login should return 400 for blank username")
    void login_shouldReturn400_forBlankUsername() throws Exception {
        LoginRequest loginRequest = new LoginRequest("", "password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/login should return 400 for blank password")
    void login_shouldReturn400_forBlankPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest("someuser", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }
}
