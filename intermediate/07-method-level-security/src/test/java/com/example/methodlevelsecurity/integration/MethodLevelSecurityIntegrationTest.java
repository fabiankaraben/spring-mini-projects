package com.example.methodlevelsecurity.integration;

import com.example.methodlevelsecurity.domain.Document;
import com.example.methodlevelsecurity.domain.Role;
import com.example.methodlevelsecurity.domain.User;
import com.example.methodlevelsecurity.domain.Visibility;
import com.example.methodlevelsecurity.repository.DocumentRepository;
import com.example.methodlevelsecurity.repository.UserRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Method Level Security mini-project.
 *
 * <h2>What is tested here</h2>
 * <p>These tests exercise the complete HTTP request-response cycle, verifying:</p>
 * <ul>
 *   <li>JWT authentication and the full security filter chain.</li>
 *   <li>{@code @PreAuthorize} enforcement (403 when caller lacks required role).</li>
 *   <li>{@code @PostAuthorize} enforcement (403 when the returned document is not
 *       accessible to the caller).</li>
 *   <li>{@code @PostFilter} enforcement (private documents hidden in list responses).</li>
 *   <li>{@code @Secured} enforcement on user management endpoints.</li>
 *   <li>Ownership-based access (owner can edit/delete own docs; non-owner gets 403).</li>
 *   <li>PostgreSQL persistence via JPA/Hibernate using a real Testcontainers DB.</li>
 * </ul>
 *
 * <h2>Testcontainers</h2>
 * <p>{@code @Testcontainers} activates the Testcontainers JUnit 5 extension that
 * manages the {@link PostgreSQLContainer} lifecycle. Declaring it {@code static}
 * starts the container once for the entire class and reuses it across all tests
 * for performance.</p>
 *
 * <h2>@DynamicPropertySource</h2>
 * <p>Testcontainers assigns a random host port at startup. This method patches
 * Spring's datasource properties at runtime with the actual container URL,
 * so Hibernate connects to the real Postgres inside the container.</p>
 *
 * <h2>Test ordering</h2>
 * <p>Tests are ordered to reflect a realistic workflow. Shared static token fields
 * accumulate JWTs obtained in early login tests and are reused in later tests.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Method Level Security – Integration Tests")
class MethodLevelSecurityIntegrationTest {

    // ── Testcontainers ────────────────────────────────────────────────────────

    /**
     * A real PostgreSQL 16 container, shared across all test methods in this class.
     * {@code static} ensures it is started once and reused.
     */
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Overwrites Spring datasource properties at runtime with the container's
     * actual JDBC URL, username, and password (assigned randomly by Testcontainers).
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Spring-injected dependencies ──────────────────────────────────────────

    /** Performs HTTP requests against the full running application context. */
    @Autowired
    private MockMvc mockMvc;

    /** Serialises request bodies to JSON strings. */
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── Shared test state ─────────────────────────────────────────────────────

    /**
     * Static JWT tokens populated by login tests and reused by subsequent tests.
     * Static fields work here because the Spring context is shared across the class.
     */
    private static String userToken;
    private static String moderatorToken;
    private static String adminToken;

    /** IDs of documents seeded in @BeforeAll, used in document access tests. */
    private static Long publicDocId;
    private static Long privateDocId;
    private static Long adminDocId;

    // ── Test data setup ───────────────────────────────────────────────────────

    /**
     * Seeds the database with users and documents before any tests run.
     * Uses {@code @BeforeAll} so this executes once for the class.
     */
    @BeforeAll
    static void seedData(@Autowired UserRepository userRepository,
                         @Autowired DocumentRepository documentRepository,
                         @Autowired PasswordEncoder passwordEncoder) {
        // Clean previous data to ensure a predictable starting state
        documentRepository.deleteAll();
        userRepository.deleteAll();

        // Create one user per role with a BCrypt-encoded password
        userRepository.save(new User("testuser",      passwordEncoder.encode("user123"),      Role.ROLE_USER));
        userRepository.save(new User("testmoderator", passwordEncoder.encode("moderator123"), Role.ROLE_MODERATOR));
        userRepository.save(new User("testadmin",     passwordEncoder.encode("admin123"),     Role.ROLE_ADMIN));

        // Create documents owned by testuser for access-control tests
        Document pub  = documentRepository.save(
                new Document("Public Doc",  "Public content",  "testuser", Visibility.PUBLIC));
        Document priv = documentRepository.save(
                new Document("Private Doc", "Private content", "testuser", Visibility.PRIVATE));
        Document adm  = documentRepository.save(
                new Document("Admin Doc",   "Admin content",   "testadmin", Visibility.PRIVATE));

        publicDocId  = pub.getId();
        privateDocId = priv.getId();
        adminDocId   = adm.getId();
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Logs in with the given credentials and returns the JWT from the response body.
     *
     * @param username plain-text username
     * @param password plain-text password
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

        Map<?, ?> response = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        return (String) response.get("token");
    }

    // ── Authentication tests ──────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/auth/login – returns JWT for ROLE_USER credentials")
    void login_user_returnsToken() throws Exception {
        userToken = loginAndGetToken("testuser", "user123");
        Assertions.assertNotNull(userToken);
        Assertions.assertFalse(userToken.isBlank());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/auth/login – returns JWT for ROLE_MODERATOR credentials")
    void login_moderator_returnsToken() throws Exception {
        moderatorToken = loginAndGetToken("testmoderator", "moderator123");
        Assertions.assertNotNull(moderatorToken);
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/auth/login – returns JWT for ROLE_ADMIN credentials")
    void login_admin_returnsToken() throws Exception {
        adminToken = loginAndGetToken("testadmin", "admin123");
        Assertions.assertNotNull(adminToken);
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login – returns 401 for wrong password")
    void login_wrongPassword_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "testuser", "password", "WRONG"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/register – creates a new user (201)")
    void register_validData_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "newuser", "password", "newpass123"));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/auth/register – returns 409 for duplicate username")
    void register_duplicateUsername_returns409() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "testuser", "password", "anything"));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // ── Document creation (@PreAuthorize("isAuthenticated()")) ────────────────

    @Test
    @Order(10)
    @DisplayName("POST /api/documents – authenticated user can create a document (201)")
    void createDocument_authenticated_returns201() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "My New Note",
                "content", "Hello, world!",
                "visibility", "PRIVATE"
        ));
        mockMvc.perform(post("/api/documents")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUsername").value("testuser"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/documents – unauthenticated request returns 401")
    void createDocument_unauthenticated_returns401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Note", "content", "Content", "visibility", "PUBLIC"));
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    // ── Get my documents (@PreAuthorize("isAuthenticated()")) ─────────────────

    @Test
    @Order(20)
    @DisplayName("GET /api/documents/me – returns own documents for ROLE_USER")
    void getMyDocuments_user_returns200() throws Exception {
        mockMvc.perform(get("/api/documents/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── Get public documents (@PostFilter) ────────────────────────────────────

    @Test
    @Order(30)
    @DisplayName("GET /api/documents/public – ROLE_USER sees only PUBLIC documents (@PostFilter)")
    void getPublicDocuments_user_seesOnlyPublic() throws Exception {
        mockMvc.perform(get("/api/documents/public")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // All returned documents must be PUBLIC (PostFilter removes PRIVATE ones)
                .andExpect(jsonPath("$[*].visibility", everyItem(equalTo("PUBLIC"))));
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/documents/public – ROLE_ADMIN sees all documents (PostFilter passes all)")
    void getPublicDocuments_admin_seesAll() throws Exception {
        mockMvc.perform(get("/api/documents/public")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // Admin bypasses the @PostFilter – should see both PUBLIC and PRIVATE
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    // ── Get all documents (@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")) ─

    @Test
    @Order(40)
    @DisplayName("GET /api/documents – ROLE_ADMIN gets full list (200)")
    void getAllDocuments_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(41)
    @DisplayName("GET /api/documents – ROLE_MODERATOR gets full list (200)")
    void getAllDocuments_moderator_returns200() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(42)
    @DisplayName("GET /api/documents – ROLE_USER gets 403 (@PreAuthorize enforced)")
    void getAllDocuments_user_returns403() throws Exception {
        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ── Get document by ID (@PostAuthorize) ───────────────────────────────────

    @Test
    @Order(50)
    @DisplayName("GET /api/documents/{id} – owner can read their own PRIVATE document")
    void getDocumentById_owner_canReadPrivate() throws Exception {
        mockMvc.perform(get("/api/documents/" + privateDocId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(privateDocId));
    }

    @Test
    @Order(51)
    @DisplayName("GET /api/documents/{id} – any authenticated user can read PUBLIC document")
    void getDocumentById_anyUser_canReadPublic() throws Exception {
        mockMvc.perform(get("/api/documents/" + publicDocId)
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
    }

    @Test
    @Order(52)
    @DisplayName("GET /api/documents/{id} – non-owner ROLE_USER gets 403 for PRIVATE doc (@PostAuthorize)")
    void getDocumentById_nonOwnerUser_gets403ForPrivate() throws Exception {
        // adminDocId is PRIVATE and owned by testadmin – testuser should be denied
        mockMvc.perform(get("/api/documents/" + adminDocId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(53)
    @DisplayName("GET /api/documents/{id} – ROLE_ADMIN can read any document")
    void getDocumentById_admin_canReadAnyDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + privateDocId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(54)
    @DisplayName("GET /api/documents/{id} – ROLE_MODERATOR can read any document")
    void getDocumentById_moderator_canReadAnyDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + privateDocId)
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk());
    }

    // ── Update document (ownership check) ─────────────────────────────────────

    @Test
    @Order(60)
    @DisplayName("PUT /api/documents/{id} – owner can update their own document")
    void updateDocument_owner_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Updated Title",
                "content", "Updated content",
                "visibility", "PUBLIC"
        ));
        mockMvc.perform(put("/api/documents/" + publicDocId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    @Order(61)
    @DisplayName("PUT /api/documents/{id} – ROLE_USER non-owner gets 403")
    void updateDocument_nonOwnerUser_returns403() throws Exception {
        // Try to update adminDocId (owned by testadmin) as testuser
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Hacked Title",
                "content", "Hacked content",
                "visibility", "PUBLIC"
        ));
        mockMvc.perform(put("/api/documents/" + adminDocId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(62)
    @DisplayName("PUT /api/documents/{id} – ROLE_ADMIN can update any document")
    void updateDocument_admin_returns200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "Admin Updated",
                "content", "Admin content",
                "visibility", "PRIVATE"
        ));
        mockMvc.perform(put("/api/documents/" + privateDocId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin Updated"));
    }

    // ── Delete document (ownership check) ─────────────────────────────────────

    @Test
    @Order(70)
    @DisplayName("DELETE /api/documents/{id} – non-owner ROLE_USER gets 403")
    void deleteDocument_nonOwnerUser_returns403() throws Exception {
        mockMvc.perform(delete("/api/documents/" + adminDocId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(71)
    @DisplayName("DELETE /api/documents/{id} – owner can delete their own document")
    void deleteDocument_owner_returns200() throws Exception {
        // Create a fresh document for deletion so other tests are not affected
        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "To Delete", "content", "Temp", "visibility", "PRIVATE"));
        MvcResult createResult = mockMvc.perform(post("/api/documents")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();

        Map<?, ?> created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), Map.class);
        Integer createdId = (Integer) created.get("id");

        mockMvc.perform(delete("/api/documents/" + createdId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("deleted")));
    }

    // ── Delete all documents of a user (@PreAuthorize with parameter binding) ─

    @Test
    @Order(80)
    @DisplayName("DELETE /api/documents/user/{u} – user can delete their own documents")
    void deleteAllDocumentsOf_self_returns200() throws Exception {
        mockMvc.perform(delete("/api/documents/user/testuser")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("testuser")));
    }

    @Test
    @Order(81)
    @DisplayName("DELETE /api/documents/user/{u} – ROLE_USER cannot delete another user's documents (403)")
    void deleteAllDocumentsOf_otherUser_returns403() throws Exception {
        // testuser (ROLE_USER) tries to delete testadmin's documents
        mockMvc.perform(delete("/api/documents/user/testadmin")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(82)
    @DisplayName("DELETE /api/documents/user/{u} – ROLE_ADMIN can delete any user's documents")
    void deleteAllDocumentsOf_admin_returns200() throws Exception {
        mockMvc.perform(delete("/api/documents/user/testadmin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ── User management (@Secured and @PreAuthorize) ──────────────────────────

    @Test
    @Order(90)
    @DisplayName("GET /api/users – ROLE_ADMIN gets full user list (@Secured enforced)")
    void getAllUsers_admin_returns200() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @Order(91)
    @DisplayName("GET /api/users – ROLE_USER gets 403 (@Secured enforced)")
    void getAllUsers_user_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(92)
    @DisplayName("GET /api/users/{id} – ROLE_MODERATOR can look up a user")
    void getUserById_moderator_returns200() throws Exception {
        User testUser = userRepository.findByUsername("testuser").orElseThrow();
        mockMvc.perform(get("/api/users/" + testUser.getId())
                        .header("Authorization", "Bearer " + moderatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    @Order(93)
    @DisplayName("GET /api/users/{id} – ROLE_USER cannot look up users (@PreAuthorize enforced)")
    void getUserById_user_returns403() throws Exception {
        mockMvc.perform(get("/api/users/1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(94)
    @DisplayName("PATCH /api/users/{id}/role – ROLE_ADMIN can update a user's role")
    void updateUserRole_admin_returns200() throws Exception {
        User testUser = userRepository.findByUsername("testuser").orElseThrow();
        String body = objectMapper.writeValueAsString(Map.of("role", "ROLE_MODERATOR"));

        mockMvc.perform(patch("/api/users/" + testUser.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newRole").value("ROLE_MODERATOR"));

        // Restore original role so subsequent tests are not affected
        testUser = userRepository.findByUsername("testuser").orElseThrow();
        testUser.setRole(Role.ROLE_USER);
        userRepository.save(testUser);
    }

    @Test
    @Order(95)
    @DisplayName("PATCH /api/users/{id}/role – ROLE_USER cannot update roles (403)")
    void updateUserRole_user_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "ROLE_ADMIN"));
        mockMvc.perform(patch("/api/users/1/role")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(96)
    @DisplayName("GET /api/users/me – any authenticated user can view their own profile")
    void getMyProfile_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }
}
