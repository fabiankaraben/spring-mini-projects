package com.example.dynamicscheduling;

import com.example.dynamicscheduling.repository.TaskConfigRepository;
import com.example.dynamicscheduling.repository.TaskExecutionLogRepository;
import com.example.dynamicscheduling.scheduling.DynamicTaskRegistry;
import org.junit.jupiter.api.BeforeEach;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Dynamic Scheduling application.
 *
 * <h2>Test infrastructure</h2>
 * <ul>
 *   <li>{@link SpringBootTest} – starts the full Spring application context including
 *       the {@link org.springframework.scheduling.TaskScheduler} and Flyway migrations.</li>
 *   <li>{@link AutoConfigureMockMvc} – injects {@link MockMvc} so we can make HTTP
 *       requests through the full Spring MVC dispatcher without a real server.</li>
 *   <li><strong>PostgreSQL Testcontainer</strong> – starts a real PostgreSQL 16
 *       container for the duration of the test class.  Flyway applies V1 (DDL) and
 *       V2 (seed data) migrations before the scheduler starts.</li>
 *   <li>{@link DynamicPropertySource} – overrides {@code spring.datasource.*} to
 *       point at the Testcontainer's dynamically assigned JDBC URL and port.</li>
 * </ul>
 *
 * <h2>Test scope</h2>
 * <ul>
 *   <li>POST /api/tasks – create a new task returns 201 with correct fields.</li>
 *   <li>GET /api/tasks – list tasks returns seeded tasks.</li>
 *   <li>GET /api/tasks/{taskName} – get single task by name.</li>
 *   <li>PATCH /api/tasks/{taskName}/interval – interval change is reflected in response
 *       and live registry immediately.</li>
 *   <li>POST /api/tasks/{taskName}/disable – task becomes disabled.</li>
 *   <li>POST /api/tasks/{taskName}/enable – task becomes enabled again.</li>
 *   <li>DELETE /api/tasks/{taskName} – task is removed from DB and registry.</li>
 *   <li>GET /api/logs – returns execution log entries.</li>
 *   <li>Validation: POST with invalid fields returns 400.</li>
 *   <li>Not found: GET on missing task returns 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Dynamic Scheduling – full integration tests")
class DynamicSchedulingIntegrationTest {

    // ── PostgreSQL Testcontainer ───────────────────────────────────────────────────

    /**
     * Static container shared across all tests in this class.
     * Using {@code static} avoids starting a new container per test method,
     * which significantly speeds up the test suite.
     *
     * <p>PostgreSQL 16 is used here to match the production Docker Compose image
     * and ensure parity between test and runtime environments.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("schedulingdb")
            .withUsername("scheduler")
            .withPassword("scheduler");

    /**
     * Overrides Spring Boot's datasource properties with the Testcontainer's
     * dynamically assigned JDBC URL and port.
     *
     * <p>Called after the container starts (so the port is known) and before
     * the Spring context is created.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** MockMvc for sending HTTP requests to the full Spring MVC stack. */
    @Autowired
    MockMvc mockMvc;

    /** Direct access to the task config repository for assertion helpers. */
    @Autowired
    TaskConfigRepository taskConfigRepository;

    /** Direct access to the execution log repository for assertion helpers. */
    @Autowired
    TaskExecutionLogRepository executionLogRepository;

    /** The in-memory registry – verified directly for live-interval assertions. */
    @Autowired
    DynamicTaskRegistry dynamicTaskRegistry;

    /**
     * Clean up any tasks created by individual tests before each test runs.
     *
     * <p>The Flyway V2 seed migration inserts 4 tasks (heartbeat, report, cleanup,
     * data-sync).  Tests that create additional tasks via POST /api/tasks must
     * clean up after themselves to avoid interference.  We delete only the tasks
     * that tests create (they all start with "test-").
     *
     * <p>We also delete all execution log rows to give each test a clean audit slate.
     */
    @BeforeEach
    void cleanUp() throws Exception {
        // Delete execution log rows to start each test fresh
        executionLogRepository.deleteAll();

        // Delete any "test-*" tasks left over from previous test runs
        String[] testTaskNames = {"test-task", "test-new", "test-disable-enable"};
        for (String name : testTaskNames) {
            try {
                mockMvc.perform(delete("/api/tasks/" + name));
            } catch (Exception ignored) {
                // Task may not exist – that is fine
            }
        }
    }

    // ── POST /api/tasks ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/tasks – creates an enabled task and returns 201")
    void createTask_validRequest_returns201() throws Exception {
        String requestBody = """
                {
                  "taskName": "test-task",
                  "description": "Integration test task",
                  "intervalMs": 5000,
                  "enabled": true
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskName").value("test-task"))
                .andExpect(jsonPath("$.configuredIntervalMs").value(5000))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("POST /api/tasks – creates a disabled task and returns 201")
    void createTask_disabledTask_returns201() throws Exception {
        String requestBody = """
                {
                  "taskName": "test-new",
                  "description": "Disabled on creation",
                  "intervalMs": 10000,
                  "enabled": false
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskName").value("test-new"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("POST /api/tasks – returns 400 when intervalMs is below minimum")
    void createTask_intervalTooSmall_returns400() throws Exception {
        String requestBody = """
                {
                  "taskName": "bad-task",
                  "intervalMs": 500,
                  "enabled": true
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/tasks – returns 400 when taskName is blank")
    void createTask_blankTaskName_returns400() throws Exception {
        String requestBody = """
                {
                  "taskName": "",
                  "intervalMs": 5000,
                  "enabled": true
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/tasks – returns 400 when taskName has invalid characters")
    void createTask_invalidTaskNameChars_returns400() throws Exception {
        String requestBody = """
                {
                  "taskName": "Invalid Name!",
                  "intervalMs": 5000,
                  "enabled": true
                }
                """;

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/tasks ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tasks – returns seeded tasks from V2 migration")
    void listTasks_returnsSeedTasks() throws Exception {
        // The V2 migration seeds heartbeat, report, cleanup, data-sync
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                .andExpect(jsonPath("$[?(@.taskName=='heartbeat')]").exists())
                .andExpect(jsonPath("$[?(@.taskName=='report')]").exists())
                .andExpect(jsonPath("$[?(@.taskName=='cleanup')]").exists());
    }

    // ── GET /api/tasks/{taskName} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/tasks/{taskName} – returns correct details for seeded task")
    void getTask_seededTask_returnsDetails() throws Exception {
        mockMvc.perform(get("/api/tasks/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName").value("heartbeat"))
                .andExpect(jsonPath("$.configuredIntervalMs").value(3000))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("GET /api/tasks/{taskName} – returns 404 for non-existent task")
    void getTask_unknownTask_returns404() throws Exception {
        mockMvc.perform(get("/api/tasks/ghost-task-xyz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── PATCH /api/tasks/{taskName}/interval ──────────────────────────────────────

    @Test
    @DisplayName("PATCH /interval – updates interval in DB and live registry immediately")
    void updateInterval_heartbeatTask_updatesDbAndRegistry() throws Exception {
        // When: update the heartbeat interval from 3000 ms to 8000 ms
        String requestBody = """
                { "intervalMs": 8000 }
                """;

        mockMvc.perform(patch("/api/tasks/heartbeat/interval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName").value("heartbeat"))
                .andExpect(jsonPath("$.configuredIntervalMs").value(8000))
                .andExpect(jsonPath("$.liveIntervalMs").value(8000));

        // Then: the live registry also reflects the new value
        assertThat(dynamicTaskRegistry.getInterval("heartbeat", 0L)).isEqualTo(8000L);

        // Restore the original interval so this test doesn't affect others
        mockMvc.perform(patch("/api/tasks/heartbeat/interval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"intervalMs\": 3000 }"));
    }

    @Test
    @DisplayName("PATCH /interval – returns 400 when intervalMs is below minimum")
    void updateInterval_tooSmall_returns400() throws Exception {
        mockMvc.perform(patch("/api/tasks/heartbeat/interval")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"intervalMs\": 500 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST /api/tasks/{taskName}/disable and /enable ────────────────────────────

    @Test
    @DisplayName("POST /disable then /enable – correctly cycles task state")
    void disableAndEnable_cyclesTaskState() throws Exception {
        // Create a test task so the seeded tasks remain unaffected
        String requestBody = """
                {
                  "taskName": "test-disable-enable",
                  "description": "Disable/enable cycle test",
                  "intervalMs": 60000,
                  "enabled": true
                }
                """;
        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When: disable the task
        mockMvc.perform(post("/api/tasks/test-disable-enable/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // Then: the registry reflects the disabled state
        assertThat(dynamicTaskRegistry.isEnabled("test-disable-enable", true)).isFalse();

        // When: re-enable the task
        mockMvc.perform(post("/api/tasks/test-disable-enable/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Then: the registry reflects the enabled state
        assertThat(dynamicTaskRegistry.isEnabled("test-disable-enable", false)).isTrue();
    }

    @Test
    @DisplayName("POST /disable – returns 400 when task is already disabled")
    void disable_alreadyDisabledTask_returns400() throws Exception {
        // The seeded "data-sync" task is created as disabled in V2 migration
        mockMvc.perform(post("/api/tasks/data-sync/disable"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── DELETE /api/tasks/{taskName} ──────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/tasks/{taskName} – removes task from DB and registry")
    void deleteTask_existingTask_removesFromDbAndRegistry() throws Exception {
        // Given: create a task to delete
        String requestBody = """
                {
                  "taskName": "test-task",
                  "description": "Task to delete",
                  "intervalMs": 5000,
                  "enabled": false
                }
                """;
        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When: delete it
        mockMvc.perform(delete("/api/tasks/test-task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: the task is no longer found via the API
        mockMvc.perform(get("/api/tasks/test-task"))
                .andExpect(status().isNotFound());

        // And: the registry no longer knows about it
        assertThat(dynamicTaskRegistry.contains("test-task")).isFalse();
    }

    // ── GET /api/logs ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/logs – returns empty page initially (after @BeforeEach cleanup)")
    void getLogs_emptyInitially_returnsEmptyPage() throws Exception {
        // @BeforeEach deletes all execution log rows
        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /api/logs – returns paginated results with correct structure")
    void getLogs_withPagination_returnsCorrectStructure() throws Exception {
        mockMvc.perform(get("/api/logs?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageable").exists())
                .andExpect(jsonPath("$.totalElements").exists());
    }
}
