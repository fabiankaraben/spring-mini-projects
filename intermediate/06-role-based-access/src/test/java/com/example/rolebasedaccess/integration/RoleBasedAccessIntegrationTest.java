package com.example.rolebasedaccess.integration;

import com.example.rolebasedaccess.domain.Role;
import com.example.rolebasedaccess.domain.User;
import com.example.rolebasedaccess.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Role-Based Access mini-project.
 *
 * <h2>What is tested here</h2>
 * <p>These tests exercise the complete HTTP request-response cycle including:</p>
 * <ul>
 *   <li>Spring MVC controllers and their {@code @PreAuthorize} annotations.</li>
 *   <li>The JWT authentication filter (token generation and validation).</li>
 *   <li>Spring Security's URL-level access rules from {@code SecurityConfig}.</li>
 *   <li>PostgreSQL persistence via JPA/Hibernate (using a real Testcontainers DB).</li>
 * </ul>
 *
 * <h2>Testcontainers</h2>
 * <p>{@code @Testcontainers} activates the Testcontainers JUnit 5 extension,
 * which manages the lifecycle of the {@code @Container} field below. The
 * PostgreSQL container is started once for the entire test class
 * ({@code static} field) and reused across all test methods for performance.</p>
 *
 * <h2>@DynamicPropertySource</h2>
 * <p>Testcontainers assigns a random host port to the container at startup.
 * {@code @DynamicPropertySource} overwrites Spring's datasource properties
 * at runtime with the actual container URL, username, and password.</p>
 *
 * <h2>@ActiveProfiles("test")</h2>
 * <p>Activates {@code application-test.yml}, which sets {@code ddl-auto: create-drop}
 * to ensure a clean schema on every test run and disables SQL console output.</p>
 *
 * <h2>Test order</h2>
 * <p>Tests are ordered via {@code @TestMethodOrder} to reflect a realistic
 * workflow (register → login → access protected endpoints). Each test builds
 * on the users created by earlier tests via shared static token fields.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Role-Based Access Integration Tests")
class RoleBasedAccessIntegrationTest {

    // ── Testcontainers PostgreSQL container ───────────────────────────────────

    /**
     * A real PostgreSQL 16 container shared across all test methods.
     * Declared {@code static} so Testcontainers starts it once for the class.
     */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Override Spring's DataSource properties with the actual container
     * connection details (host, port, DB name, credentials).
     * Called by Spring before the application context is loaded.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Spring-injected test dependencies ─────────────────────────────────────

    /** MockMvc performs HTTP requests against the running application context. */
    @Autowired
    private MockMvc mockMvc;

    /** Used to serialise request bodies to JSON. */
    @Autowired
    private ObjectMapper objectMapper;

    /** Direct access to the repository to set up test data. */
    @Autowired
    private UserRepository userRepository;

    // ── Shared token state ────────────────────────────────────────────────────

    /**
     * JWT tokens extracted from the login responses.
     * They are stored as static fields so later test methods can reuse them.
     * Using static fields works because the container and application context
     * are shared for the entire test class.
     */
    private static String userToken;
    private static String moderatorToken;
    private static String adminToken;

    // ── Test data setup ───────────────────────────────────────────────────────

    /**
     * Seeds the database with one user per role before tests run.
     * Uses {@code @BeforeAll} so this runs once for the class.
     */
    @BeforeAll
    static void seedUsers(@Autowired UserRepository userRepository,
                          @Autowired PasswordEncoder passwordEncoder) {
        // Clear any residual data from a previous run
        userRepository.deleteAll();

        // Create one user per role; passwords are BCrypt-encoded
        userRepository.save(new User("testuser",      passwordEncoder.encode("user123"),      Role.ROLE_USER));
        userRepository.save(new User("testmoderator", passwordEncoder.encode("moderator123"), Role.ROLE_MODERATOR));
        userRepository.save(new User("testadmin",     passwordEncoder.encode("admin123"),     Role.ROLE_ADMIN));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Logs in with the given credentials and returns the JWT from the response.
     *
     * @param username the username
     * @param password the plain-text password
     * @return the signed JWT string
     */
    private String loginAndGetToken(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        // Parse the JWT from the JSON response body
        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("token");
    }

    // ── Authentication tests ──────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/login – returns JWT for valid ROLE_USER credentials")
    void login_withValidUserCredentials_returnsToken() throws Exception {
        userToken = loginAndGetToken("testuser", "user123");
        Assertions.assertNotNull(userToken, "Token must not be null after successful login");
        Assertions.assertFalse(userToken.isBlank(), "Token must not be blank");
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/login – returns JWT for valid ROLE_MODERATOR credentials")
    void login_withValidModeratorCredentials_returnsToken() throws Exception {
        moderatorToken = loginAndGetToken("testmoderator", "moderator123");
        Assertions.assertNotNull(moderatorToken);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/login – returns JWT for valid ROLE_ADMIN credentials")
    void login_withValidAdminCredentials_returnsToken() throws Exception {
        adminToken = loginAndGetToken("testadmin", "admin123");
        Assertions.assertNotNull(adminToken);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login – returns 401 for invalid credentials")
    void login_withInvalidCredentials_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "testuser",
                "password", "WRONG_PASSWORD"
        ));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/register – creates a new user (201 Created)")
    void register_withValidData_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "newuser",
                "password", "newpass123"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/auth/register – returns 409 when username already exists")
    void register_withDuplicateUsername_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "testuser",   // already registered in @BeforeAll
                "password", "somepass"
        ));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── GET /api/users/me (any authenticated user) ────────────────────────────

    @Test
    @Order(10)
    @DisplayName("GET /api/users/me – returns profile for ROLE_USER token")
    void getMyProfile_withUserToken_returns200() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/users/me – returns 401 without a token")
    void getMyProfile_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/users (ADMIN only) ───────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("GET /api/users – returns 200 with user list for ROLE_ADMIN")
    void getAllUsers_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/users – returns 403 for ROLE_USER")
    void getAllUsers_withUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(22)
    @DisplayName("GET /api/users – returns 403 for ROLE_MODERATOR")
    void getAllUsers_withModeratorToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/users/{id} (ADMIN and MODERATOR) ─────────────────────────────

    @Test
    @Order(30)
    @DisplayName("GET /api/users/{id} – returns 200 for ROLE_MODERATOR")
    void getUserById_withModeratorToken_returns200() throws Exception {
        // Find the id of testuser
        User testUser = userRepository.findByUsername("testuser").orElseThrow();

        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/users/{id} – returns 403 for ROLE_USER")
    void getUserById_withUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/users/{id}/role (ADMIN only) ───────────────────────────────

    @Test
    @Order(40)
    @DisplayName("PATCH /api/users/{id}/role – returns 200 and updates role for ROLE_ADMIN")
    void updateUserRole_withAdminToken_returns200() throws Exception {
        User testUser = userRepository.findByUsername("testuser").orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("role", "ROLE_MODERATOR"));

        mockMvc.perform(patch("/api/users/" + testUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newRole").value("ROLE_MODERATOR"));

        // Restore original role for subsequent tests
        testUser = userRepository.findByUsername("testuser").orElseThrow();
        testUser.setRole(Role.ROLE_USER);
        userRepository.save(testUser);
    }

    @Test
    @Order(41)
    @DisplayName("PATCH /api/users/{id}/role – returns 403 for ROLE_USER")
    void updateUserRole_withUserToken_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "ROLE_ADMIN"));

        mockMvc.perform(patch("/api/users/1/role")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/admin/** (ADMIN only – URL-level + @PreAuthorize) ────────────

    @Test
    @Order(50)
    @DisplayName("GET /api/admin/dashboard – returns 200 for ROLE_ADMIN")
    void adminDashboard_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("testadmin")));
    }

    @Test
    @Order(51)
    @DisplayName("GET /api/admin/dashboard – returns 403 for ROLE_USER (URL-level rule fires)")
    void adminDashboard_withUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(52)
    @DisplayName("GET /api/admin/system – returns 200 for ROLE_ADMIN")
    void adminSystem_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/system")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    // ── GET /api/moderator/** (MODERATOR and ADMIN) ───────────────────────────

    @Test
    @Order(60)
    @DisplayName("GET /api/moderator/panel – returns 200 for ROLE_MODERATOR")
    void moderatorPanel_withModeratorToken_returns200() throws Exception {
        mockMvc.perform(get("/api/moderator/panel")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("testmoderator")));
    }

    @Test
    @Order(61)
    @DisplayName("GET /api/moderator/panel – returns 200 for ROLE_ADMIN (admins can access moderator endpoints)")
    void moderatorPanel_withAdminToken_returns200() throws Exception {
        mockMvc.perform(get("/api/moderator/panel")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(62)
    @DisplayName("GET /api/moderator/panel – returns 403 for ROLE_USER")
    void moderatorPanel_withUserToken_returns403() throws Exception {
        mockMvc.perform(get("/api/moderator/panel")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(63)
    @DisplayName("GET /api/moderator/reports – returns 200 for ROLE_MODERATOR")
    void moderatorReports_withModeratorToken_returns200() throws Exception {
        mockMvc.perform(get("/api/moderator/reports")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReports").isNumber());
    }

    // ── DELETE /api/users/{id} (ADMIN only) ───────────────────────────────────

    @Test
    @Order(70)
    @DisplayName("DELETE /api/users/{id} – returns 403 for ROLE_MODERATOR")
    void deleteUser_withModeratorToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/999")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(71)
    @DisplayName("DELETE /api/users/{id} – returns 404 for non-existent user (ROLE_ADMIN)")
    void deleteUser_withAdminToken_nonExistentUser_returns404() throws Exception {
        mockMvc.perform(delete("/api/users/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
