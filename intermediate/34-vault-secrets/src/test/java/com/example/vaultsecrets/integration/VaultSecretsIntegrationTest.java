package com.example.vaultsecrets.integration;

import com.example.vaultsecrets.repository.CredentialEntryRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Vault Secrets application using Testcontainers.
 *
 * <h2>Key concepts demonstrated</h2>
 * <ul>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension</li>
 *   <li>{@code @Container} — declares a HashiCorp Vault Docker container scoped to
 *       this test class (class-level lifecycle: started once, shared across all tests)</li>
 *   <li>{@code @DynamicPropertySource} — injects the Vault container's host/port into
 *       the Spring context before it starts, so Spring Cloud Vault connects to the
 *       containerised Vault instead of a real server</li>
 *   <li>{@code @SpringBootTest(webEnvironment = RANDOM_PORT)} + {@code @AutoConfigureMockMvc}
 *       — boots the full application context and exposes HTTP-level testing via MockMvc</li>
 * </ul>
 *
 * <h2>Container lifecycle</h2>
 * <p>The static {@code @Container} field ensures a single Vault container is started
 * once before all tests in this class and stopped after the last test. This avoids
 * the overhead of starting/stopping a container per test method.</p>
 *
 * <h2>Vault setup</h2>
 * <p>The container is started with the root token {@code "test-root-token"} and the
 * KV v2 secrets engine pre-enabled at the {@code "secret"} mount path (done via
 * {@code withSecretInVault} / environment configuration).</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Vault Secrets Integration Tests")
class VaultSecretsIntegrationTest {

    /**
     * The root token used by the Vault dev-mode server.
     * This must match the token configured in {@link #configureProperties}.
     */
    private static final String VAULT_TOKEN = "test-root-token";

    /**
     * Testcontainers Vault container using the official HashiCorp Vault image.
     *
     * <p>The container is started in "dev mode" (via the {@code -dev} flag),
     * which means:</p>
     * <ul>
     *   <li>Vault is pre-unsealed — no unseal keys needed.</li>
     *   <li>The KV v2 secrets engine is pre-mounted at {@code "secret/"}.</li>
     *   <li>All data is stored in memory — no persistence between runs.</li>
     * </ul>
     *
     * <p>Dev mode is perfect for integration tests: fast startup, no configuration,
     * always in a clean state.</p>
     */
    @Container
    static VaultContainer<?> vaultContainer =
            new VaultContainer<>(DockerImageName.parse("hashicorp/vault:1.17"))
                    // Set the root token for the dev server
                    .withVaultToken(VAULT_TOKEN);

    /**
     * Injects Vault connection properties into the Spring context dynamically.
     *
     * <p>This method runs after the container starts but before the Spring context
     * is created. It overrides {@code application.yml} settings with the actual
     * host and port assigned by Docker (ephemeral).</p>
     *
     * @param registry Spring's dynamic property registry
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Point Spring Cloud Vault to the running container
        registry.add("spring.cloud.vault.host", vaultContainer::getHost);
        registry.add("spring.cloud.vault.port", vaultContainer::getFirstMappedPort);
        registry.add("spring.cloud.vault.scheme", () -> "http");

        // Use the same root token that the container was started with
        registry.add("spring.cloud.vault.token", () -> VAULT_TOKEN);

        // Disable Spring Cloud Vault's automatic property sourcing at startup
        // (we only use VaultTemplate for on-demand reads/writes in this demo)
        registry.add("spring.cloud.vault.kv.enabled", () -> "false");
        registry.add("spring.cloud.vault.config.lifecycle.enabled", () -> "false");
    }

    /** MockMvc allows HTTP-level testing against the full Spring context. */
    @Autowired
    private MockMvc mockMvc;

    /** Direct repository access for cleanup between tests. */
    @Autowired
    private CredentialEntryRepository credentialEntryRepository;

    /**
     * Clears all metadata from the H2 database before each test.
     * This ensures test isolation — data written in one test does not bleed into others.
     * (Vault data is in-memory inside the container and is also isolated per test
     * because each test writes to unique paths.)
     */
    @BeforeEach
    void cleanDatabase() {
        credentialEntryRepository.deleteAll();
    }

    // =========================================================================
    // POST /api/credentials — Store a new secret
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /api/credentials — should store secret in Vault and return 201")
    void storeSecret_returnsCreated() throws Exception {
        String requestBody = """
                {
                    "name": "prod-db",
                    "vaultPath": "myapp/db",
                    "description": "Production DB credentials",
                    "secretData": {
                        "username": "admin",
                        "password": "s3cr3t"
                    }
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("prod-db"))
                .andExpect(jsonPath("$.vaultPath").value("myapp/db"))
                .andExpect(jsonPath("$.description").value("Production DB credentials"))
                // The response MUST NOT contain secret values
                .andExpect(jsonPath("$.secretData").doesNotExist())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/credentials — should return 400 when name is blank")
    void storeSecret_returns400_whenNameBlank() throws Exception {
        String requestBody = """
                {
                    "name": "",
                    "vaultPath": "myapp/db",
                    "secretData": {"key": "val"}
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/credentials — should return 400 when vaultPath is blank")
    void storeSecret_returns400_whenVaultPathBlank() throws Exception {
        String requestBody = """
                {
                    "name": "my-cred",
                    "vaultPath": "",
                    "secretData": {"key": "val"}
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/credentials — should return 400 when secretData is empty")
    void storeSecret_returns400_whenSecretDataEmpty() throws Exception {
        String requestBody = """
                {
                    "name": "my-cred",
                    "vaultPath": "myapp/test",
                    "secretData": {}
                }
                """;

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/credentials — should return 409 when name is duplicate")
    void storeSecret_returns409_whenDuplicateName() throws Exception {
        // First store succeeds
        String requestBody = """
                {
                    "name": "duplicate-cred",
                    "vaultPath": "myapp/dup1",
                    "secretData": {"key": "val1"}
                }
                """;
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // Second store with same name returns 409
        String requestBody2 = """
                {
                    "name": "duplicate-cred",
                    "vaultPath": "myapp/dup2",
                    "secretData": {"key": "val2"}
                }
                """;
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("duplicate-cred")));
    }

    // =========================================================================
    // GET /api/credentials — List all credentials
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("GET /api/credentials — should return empty list when no credentials exist")
    void listCredentials_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/credentials — should return all registered credentials")
    void listCredentials_returnsAllCredentials() throws Exception {
        // Seed two credentials
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"cred-a","vaultPath":"myapp/a","secretData":{"k":"v"}}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"cred-b","vaultPath":"myapp/b","secretData":{"k":"v"}}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("cred-a", "cred-b")))
                // Secret data must NEVER appear in the list response
                .andExpect(jsonPath("$[*].secretData").doesNotExist());
    }

    // =========================================================================
    // GET /api/credentials/{name} — Get metadata
    // =========================================================================

    @Test
    @Order(20)
    @DisplayName("GET /api/credentials/{name} — should return metadata for existing credential")
    void getMetadata_returnsMetadata() throws Exception {
        // Seed one credential
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name":"meta-test",
                                    "vaultPath":"myapp/meta",
                                    "description":"Metadata test cred",
                                    "secretData":{"token":"abc123"}
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/credentials/meta-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("meta-test"))
                .andExpect(jsonPath("$.vaultPath").value("myapp/meta"))
                .andExpect(jsonPath("$.description").value("Metadata test cred"))
                // Secret data must NEVER appear in metadata response
                .andExpect(jsonPath("$.secretData").doesNotExist());
    }

    @Test
    @Order(21)
    @DisplayName("GET /api/credentials/{name} — should return 404 for unknown name")
    void getMetadata_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/credentials/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // GET /api/credentials/{name}/secret — Retrieve secret from Vault
    // =========================================================================

    @Test
    @Order(30)
    @DisplayName("GET /api/credentials/{name}/secret — should return secret data from Vault")
    void getSecret_returnsSecretFromVault() throws Exception {
        // Seed one credential with known secret data
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name":"api-key",
                                    "vaultPath":"myapp/api",
                                    "secretData":{
                                        "apiKey":"super-secret-key-123",
                                        "endpoint":"https://api.example.com"
                                    }
                                }
                                """))
                .andExpect(status().isCreated());

        // Retrieve the actual secret from Vault
        mockMvc.perform(get("/api/credentials/api-key/secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("super-secret-key-123"))
                .andExpect(jsonPath("$.endpoint").value("https://api.example.com"));
    }

    @Test
    @Order(31)
    @DisplayName("GET /api/credentials/{name}/secret — should return 404 when name not registered")
    void getSecret_returns404_whenNameNotFound() throws Exception {
        mockMvc.perform(get("/api/credentials/nonexistent/secret"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("nonexistent")));
    }

    // =========================================================================
    // DELETE /api/credentials/{name} — Delete credential
    // =========================================================================

    @Test
    @Order(40)
    @DisplayName("DELETE /api/credentials/{name} — should delete credential from Vault and metadata")
    void deleteCredential_returnsNoContent() throws Exception {
        // Seed a credential
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name":"to-delete",
                                    "vaultPath":"myapp/delete-me",
                                    "secretData":{"temp":"data"}
                                }
                                """))
                .andExpect(status().isCreated());

        // Delete it
        mockMvc.perform(delete("/api/credentials/to-delete"))
                .andExpect(status().isNoContent());

        // Verify metadata is gone
        mockMvc.perform(get("/api/credentials/to-delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(41)
    @DisplayName("DELETE /api/credentials/{name} — should return 404 when not registered")
    void deleteCredential_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/credentials/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(containsString("nonexistent")));
    }

    // =========================================================================
    // Full round-trip test
    // =========================================================================

    @Test
    @Order(50)
    @DisplayName("Full round-trip: store → list → get metadata → retrieve secret → delete")
    void fullRoundTrip() throws Exception {
        // 1. Store a secret
        mockMvc.perform(post("/api/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name":"round-trip-cred",
                                    "vaultPath":"myapp/roundtrip",
                                    "description":"Round-trip test credential",
                                    "secretData":{
                                        "username":"rt-user",
                                        "password":"rt-pass"
                                    }
                                }
                                """))
                .andExpect(status().isCreated());

        // 2. Verify it appears in the list
        mockMvc.perform(get("/api/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("round-trip-cred")));

        // 3. Get metadata
        mockMvc.perform(get("/api/credentials/round-trip-cred"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Round-trip test credential"));

        // 4. Retrieve the actual secret from Vault
        mockMvc.perform(get("/api/credentials/round-trip-cred/secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("rt-user"))
                .andExpect(jsonPath("$.password").value("rt-pass"));

        // 5. Delete the credential
        mockMvc.perform(delete("/api/credentials/round-trip-cred"))
                .andExpect(status().isNoContent());

        // 6. Verify it is gone from the list
        mockMvc.perform(get("/api/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", not(hasItem("round-trip-cred"))));
    }
}
