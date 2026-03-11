package com.example.validationgroups.integration;

import com.example.validationgroups.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Validation Groups API.
 *
 * <h2>What these tests verify</h2>
 * <ul>
 *   <li><strong>OnCreate group</strong> – POST /api/users requires name, email, password,
 *       and role; missing any of these produces HTTP 400 with a field error.</li>
 *   <li><strong>OnUpdate group</strong> – PATCH /api/users/{id} requires name and email;
 *       omitting password is accepted (no validation error).</li>
 *   <li><strong>OnPasswordChange group</strong> – PUT /api/users/{id}/password requires
 *       newPassword and confirmPassword; name/email/password/role are silently ignored.</li>
 *   <li><strong>Cross-field validation</strong> – changing a password with mismatched
 *       confirmPassword returns HTTP 400.</li>
 *   <li><strong>Email uniqueness</strong> – registering with an existing email returns HTTP 409.</li>
 *   <li><strong>CRUD operations</strong> – create, read, update, delete work end-to-end.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – a real PostgreSQL Docker container is started for
 *       the test class, ensuring schema creation and constraint enforcement work correctly.</li>
 *   <li><strong>MockMvc</strong> – HTTP requests go through the full Spring MVC stack
 *       (including validation) without binding to a real network port.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects Testcontainers' runtime
 *       JDBC URL into the Spring context before startup.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Validation Groups API – Integration Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ValidationGroupsIntegrationTest {

    /**
     * PostgreSQL Testcontainer shared across all test methods in this class.
     *
     * <p>The {@code static} keyword ensures the container starts once before the
     * Spring ApplicationContext is created, which is required for
     * {@link #overrideDataSourceProperties} to inject the correct JDBC URL.</p>
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("validationgroups_test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Injects Testcontainers' runtime-assigned JDBC URL, username, and password
     * into the Spring DataSource configuration before the ApplicationContext starts.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** Starts the container before any test in this class runs. */
    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    /** Stops and removes the container after all tests have finished. */
    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    /** Wipe all users between tests to prevent data collisions. */
    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Creates a user via the REST API and returns the generated ID.
     */
    private Long createUser(String name, String email,
                             String password, String role) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(name, email, password, role);

        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ── OnCreate group – POST /api/users ──────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/users – OnCreate: all required fields present → HTTP 201")
    void create_withAllRequiredFields_shouldReturn201() throws Exception {
        String body = """
                {
                  "name": "John Doe",
                  "email": "john@example.com",
                  "password": "securePass1",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.email", is("john@example.com")))
                .andExpect(jsonPath("$.role", is("USER")))
                // KEY: password must NOT appear in the response
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/users – OnCreate: missing password → HTTP 400 with field error on 'password'")
    void create_withMissingPassword_shouldReturn400() throws Exception {
        // OnCreate requires password; omitting it must trigger a validation error
        String body = """
                {
                  "name": "Jane Doe",
                  "email": "jane@example.com",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                // The 'password' field error must be present
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/users – OnCreate: missing role → HTTP 400 with field error on 'role'")
    void create_withMissingRole_shouldReturn400() throws Exception {
        // OnCreate requires role; omitting it must trigger a validation error
        String body = """
                {
                  "name": "Jane Doe",
                  "email": "jane@example.com",
                  "password": "securePass1"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role", notNullValue()));
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/users – OnCreate: invalid role value → HTTP 400 with field error on 'role'")
    void create_withInvalidRole_shouldReturn400() throws Exception {
        // Role must match pattern USER|ADMIN|MODERATOR
        String body = """
                {
                  "name": "Jane Doe",
                  "email": "jane@example.com",
                  "password": "securePass1",
                  "role": "SUPERUSER"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role", notNullValue()));
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/users – OnCreate: invalid email format → HTTP 400 with field error on 'email'")
    void create_withInvalidEmail_shouldReturn400() throws Exception {
        String body = """
                {
                  "name": "Jane Doe",
                  "email": "not-an-email",
                  "password": "securePass1",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()));
    }

    @Test
    @Order(6)
    @DisplayName("POST /api/users – OnCreate: password too short → HTTP 400 with field error on 'password'")
    void create_withShortPassword_shouldReturn400() throws Exception {
        // OnCreate enforces @Size(min=8) on password
        String body = """
                {
                  "name": "Jane Doe",
                  "email": "jane@example.com",
                  "password": "short",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    @Order(7)
    @DisplayName("POST /api/users – duplicate email → HTTP 409 Conflict")
    void create_withDuplicateEmail_shouldReturn409() throws Exception {
        // Create first user
        createUser("First User", "dup@example.com", "password123", "USER");

        // Attempt to create second user with same email
        String body = """
                {
                  "name": "Second User",
                  "email": "dup@example.com",
                  "password": "password456",
                  "role": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.message", containsString("dup@example.com")));
    }

    // ── OnUpdate group – PATCH /api/users/{id} ────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("PATCH /api/users/{id} – OnUpdate: valid name and email → HTTP 200, password NOT required")
    void update_withValidNameAndEmail_shouldReturn200_andPasswordNotRequired() throws Exception {
        Long id = createUser("Original Name", "original@example.com", "password123", "USER");

        // KEY TEST: no 'password' field in the request – should NOT cause a validation error
        String body = """
                {
                  "name": "Updated Name",
                  "email": "updated@example.com"
                }
                """;

        mockMvc.perform(patch("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")))
                .andExpect(jsonPath("$.email", is("updated@example.com")))
                // Role must remain unchanged (update endpoint cannot change it)
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    @Order(9)
    @DisplayName("PATCH /api/users/{id} – OnUpdate: role in request body is ignored (not validated or applied)")
    void update_withRoleInBody_shouldIgnoreRole() throws Exception {
        Long id = createUser("Admin User", "admin@example.com", "password123", "ADMIN");

        // Sending 'role' in an update request: the validator ignores it (no OnUpdate constraint)
        // and the service does NOT apply it
        String body = """
                {
                  "name": "Admin User Renamed",
                  "email": "admin.renamed@example.com",
                  "role": "USER"
                }
                """;

        mockMvc.perform(patch("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Admin User Renamed")))
                // Role must still be ADMIN – the update request cannot change it
                .andExpect(jsonPath("$.role", is("ADMIN")));
    }

    @Test
    @Order(10)
    @DisplayName("PATCH /api/users/{id} – OnUpdate: blank name → HTTP 400")
    void update_withBlankName_shouldReturn400() throws Exception {
        Long id = createUser("Name", "user@example.com", "password123", "USER");

        String body = """
                {
                  "name": "",
                  "email": "user@example.com"
                }
                """;

        mockMvc.perform(patch("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name", notNullValue()));
    }

    @Test
    @Order(11)
    @DisplayName("PATCH /api/users/{id} – OnUpdate: non-existent ID → HTTP 404")
    void update_withNonExistentId_shouldReturn404() throws Exception {
        String body = """
                {
                  "name": "Ghost User",
                  "email": "ghost@example.com"
                }
                """;

        mockMvc.perform(patch("/api/users/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    // ── OnPasswordChange group – PUT /api/users/{id}/password ─────────────────

    @Test
    @Order(12)
    @DisplayName("PUT /api/users/{id}/password – OnPasswordChange: valid matching passwords → HTTP 204")
    void changePassword_withMatchingPasswords_shouldReturn204() throws Exception {
        Long id = createUser("Pass User", "passuser@example.com", "oldPassword1", "USER");

        // KEY TEST: only newPassword and confirmPassword are required/validated
        // name, email, password, role are all ignored
        String body = """
                {
                  "newPassword": "newSecurePass1",
                  "confirmPassword": "newSecurePass1"
                }
                """;

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(13)
    @DisplayName("PUT /api/users/{id}/password – OnPasswordChange: missing newPassword → HTTP 400")
    void changePassword_withMissingNewPassword_shouldReturn400() throws Exception {
        Long id = createUser("Pass User2", "passuser2@example.com", "oldPassword1", "USER");

        String body = """
                {
                  "confirmPassword": "newSecurePass1"
                }
                """;

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword", notNullValue()));
    }

    @Test
    @Order(14)
    @DisplayName("PUT /api/users/{id}/password – OnPasswordChange: passwords do not match → HTTP 400")
    void changePassword_withMismatchedPasswords_shouldReturn400() throws Exception {
        Long id = createUser("Pass User3", "passuser3@example.com", "oldPassword1", "USER");

        String body = """
                {
                  "newPassword": "newSecurePass1",
                  "confirmPassword": "differentPassword"
                }
                """;

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("do not match")));
    }

    @Test
    @Order(15)
    @DisplayName("PUT /api/users/{id}/password – OnPasswordChange: new password too short → HTTP 400")
    void changePassword_withShortNewPassword_shouldReturn400() throws Exception {
        Long id = createUser("Pass User4", "passuser4@example.com", "oldPassword1", "USER");

        String body = """
                {
                  "newPassword": "short",
                  "confirmPassword": "short"
                }
                """;

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword", notNullValue()));
    }

    @Test
    @Order(16)
    @DisplayName("PUT /api/users/{id}/password – OnPasswordChange: name field in body is ignored (no error)")
    void changePassword_withExtraNameField_shouldIgnoreItAndReturn204() throws Exception {
        Long id = createUser("Pass User5", "passuser5@example.com", "oldPassword1", "USER");

        // KEY TEST: name and email are sent but belong to OnCreate/OnUpdate groups.
        // With OnPasswordChange active, those fields are completely ignored by the validator.
        String body = """
                {
                  "name": "",
                  "email": "invalid-email",
                  "newPassword": "newSecurePass1",
                  "confirmPassword": "newSecurePass1"
                }
                """;

        // The blank name and invalid email must NOT cause a validation error
        // because those constraints only activate for OnCreate/OnUpdate groups,
        // not for OnPasswordChange.
        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    // ── Read endpoints ────────────────────────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("GET /api/users – returns list of all users")
    void listAll_shouldReturnAllUsers() throws Exception {
        createUser("Alice", "alice@example.com", "password123", "USER");
        createUser("Bob", "bob@example.com", "password123", "ADMIN");

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", hasItems("Alice", "Bob")));
    }

    @Test
    @Order(18)
    @DisplayName("GET /api/users/{id} – returns existing user")
    void getById_shouldReturnUser() throws Exception {
        Long id = createUser("Carol", "carol@example.com", "password123", "MODERATOR");

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Carol")))
                .andExpect(jsonPath("$.role", is("MODERATOR")));
    }

    @Test
    @Order(19)
    @DisplayName("GET /api/users/{id} – non-existent ID returns HTTP 404")
    void getById_withNonExistentId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/users/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    @Test
    @Order(20)
    @DisplayName("GET /api/users/search?name=... – returns matching users")
    void search_shouldReturnMatchingUsers() throws Exception {
        createUser("David Search", "david@example.com", "password123", "USER");

        mockMvc.perform(get("/api/users/search").param("name", "David"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("David Search")));
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/users/role/{role} – returns users with matching role")
    void byRole_shouldReturnUsersWithRole() throws Exception {
        createUser("Admin One", "adminone@example.com", "password123", "ADMIN");
        createUser("Regular User", "regular@example.com", "password123", "USER");

        mockMvc.perform(get("/api/users/role/ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Admin One")));
    }

    // ── Delete endpoint ───────────────────────────────────────────────────────

    @Test
    @Order(22)
    @DisplayName("DELETE /api/users/{id} – removes user and returns HTTP 204")
    void delete_shouldRemoveUser() throws Exception {
        Long id = createUser("To Delete", "todelete@example.com", "password123", "USER");

        mockMvc.perform(delete("/api/users/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(23)
    @DisplayName("DELETE /api/users/{id} – non-existent ID returns HTTP 404")
    void delete_withNonExistentId_shouldReturn404() throws Exception {
        mockMvc.perform(delete("/api/users/999999"))
                .andExpect(status().isNotFound());
    }

    // ── Full validation group lifecycle ───────────────────────────────────────

    @Test
    @Order(24)
    @DisplayName("Full lifecycle: create (OnCreate) → update (OnUpdate, no password) → change password (OnPasswordChange)")
    void fullLifecycle_demonstratingAllThreeGroups() throws Exception {
        // Step 1: Create – OnCreate group enforces name, email, password, role
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Full Lifecycle User",
                                  "email": "lifecycle@example.com",
                                  "password": "initialPass1",
                                  "role": "USER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("USER")))
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Step 2: Update – OnUpdate group requires name and email; password is NOT required
        mockMvc.perform(patch("/api/users/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lifecycle User Updated",
                                  "email": "lifecycle.updated@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Lifecycle User Updated")))
                .andExpect(jsonPath("$.email", is("lifecycle.updated@example.com")))
                // Role must remain USER even though we did not send it
                .andExpect(jsonPath("$.role", is("USER")));

        // Step 3: Change password – OnPasswordChange group requires only newPassword/confirmPassword
        mockMvc.perform(put("/api/users/" + id + "/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "updatedPass123",
                                  "confirmPassword": "updatedPass123"
                                }
                                """))
                .andExpect(status().isNoContent());

        // Verify the user still exists with updated name
        mockMvc.perform(get("/api/users/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Lifecycle User Updated")));

        // Verify the password was actually changed by checking that the old password
        // is no longer stored (we do this indirectly via direct repository access)
        var user = userRepository.findById(id).orElseThrow();
        assertThat(user.getPassword()).isEqualTo("updatedPass123");
        assertThat(user.getName()).isEqualTo("Lifecycle User Updated");
        assertThat(user.getRole()).isEqualTo("USER"); // role unchanged throughout
    }
}
