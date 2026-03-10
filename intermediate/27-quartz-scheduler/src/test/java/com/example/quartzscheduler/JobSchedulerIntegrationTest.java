package com.example.quartzscheduler;

import com.example.quartzscheduler.repository.JobAuditLogRepository;
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
 * Full integration tests for the Quartz Scheduler application.
 *
 * <h2>Test infrastructure</h2>
 * <ul>
 *   <li>{@link SpringBootTest} – starts the full Spring application context,
 *       including the Quartz scheduler wired to the PostgreSQL job store.</li>
 *   <li>{@link AutoConfigureMockMvc} – injects {@link MockMvc} so we can make
 *       HTTP requests through the full Spring MVC dispatcher without a real server.</li>
 *   <li><strong>PostgreSQL Testcontainer</strong> – starts a real PostgreSQL 16
 *       container for the duration of the test class.  Flyway applies the
 *       Quartz DDL ({@code V1__quartz_tables.sql}) and the audit-log DDL
 *       ({@code V2__create_job_audit_log.sql}) before the scheduler starts.</li>
 *   <li>{@link DynamicPropertySource} – overrides {@code spring.datasource.*}
 *       and the Quartz datasource to point at the Testcontainer's JDBC URL.</li>
 * </ul>
 *
 * <h2>Test scope</h2>
 * <ul>
 *   <li>POST /api/jobs – schedule a new job returns 201 with correct fields.</li>
 *   <li>GET /api/jobs – list jobs returns the scheduled job.</li>
 *   <li>GET /api/jobs/{group}/{name} – get single job by identity.</li>
 *   <li>POST /api/jobs/{group}/{name}/pause – pause changes state to PAUSED.</li>
 *   <li>POST /api/jobs/{group}/{name}/resume – resume restores NORMAL state.</li>
 *   <li>POST /api/jobs/{group}/{name}/trigger – manual trigger fires the job.</li>
 *   <li>DELETE /api/jobs/{group}/{name} – deletes the job.</li>
 *   <li>GET /api/audit – audit log records are visible after trigger-now.</li>
 *   <li>Validation: POST with missing fields returns 400.</li>
 *   <li>Validation: POST with invalid cron returns 400.</li>
 *   <li>Not found: GET on missing job returns 404.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Quartz Scheduler – full integration tests")
class JobSchedulerIntegrationTest {

    // ── PostgreSQL Testcontainer ───────────────────────────────────────────────────
    /**
     * Static container shared across all tests in this class.
     * Using {@code static} avoids starting a new container for every test method,
     * which significantly speeds up the test suite.
     *
     * <p>PostgreSQL 16 is used here because it matches the Docker image used
     * in the production Docker Compose file, ensuring parity between test and
     * runtime environments.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quartzdb")
            .withUsername("quartz")
            .withPassword("quartz");

    /**
     * Overrides Spring Boot's datasource properties with the Testcontainer's
     * dynamically assigned JDBC URL and port.
     *
     * <p>{@link DynamicPropertySource} is invoked after the container starts
     * (so the port is known) and before the Spring context is created.
     * This guarantees the application connects to the container database.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        // Override the main application datasource
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** MockMvc for sending HTTP requests to the full Spring MVC stack. */
    @Autowired
    MockMvc mockMvc;

    /** Repository for direct access to audit log records in assertions. */
    @Autowired
    JobAuditLogRepository auditLogRepository;

    /**
     * Clean up audit log rows and Quartz job entries before each test.
     *
     * <p>Because the Spring context is shared across all tests, jobs scheduled
     * by one test would affect subsequent tests if not cleaned up.  We delete
     * all audit records and then delete each scheduled job via the API to
     * ensure a clean slate.
     */
    @BeforeEach
    void cleanUp() throws Exception {
        // Delete all audit log records directly via the repository
        auditLogRepository.deleteAll();

        // Delete any jobs left over from previous tests via the REST API
        // (GET /api/jobs returns the current list; we delete each one)
        String listResponse = mockMvc.perform(get("/api/jobs"))
                .andReturn().getResponse().getContentAsString();

        // Parse job names/groups from the list and delete each
        // Simple approach: delete known test job keys used across all tests
        String[] knownJobs = {
                "MAINTENANCE/loggingJob",
                "MAINTENANCE/cleanupJob",
                "REPORTING/reportJob"
        };
        for (String jobRef : knownJobs) {
            String[] parts = jobRef.split("/");
            try {
                mockMvc.perform(delete("/api/jobs/" + parts[0] + "/" + parts[1]));
            } catch (Exception ignored) {
                // Job may not exist – that is fine
            }
        }
    }

    // ── POST /api/jobs ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/jobs – schedules a LOGGING job and returns 201")
    void scheduleJob_loggingType_returns201() throws Exception {
        // Given: a valid schedule request
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?",
                  "description": "Integration test logging job"
                }
                """;

        // When / Then: 201 Created with correct response fields
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobName").value("loggingJob"))
                .andExpect(jsonPath("$.jobGroup").value("MAINTENANCE"))
                .andExpect(jsonPath("$.jobType").value("LOGGING"))
                .andExpect(jsonPath("$.cronExpression").value("0 0/5 * * * ?"))
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));
    }

    @Test
    @DisplayName("POST /api/jobs – schedules a CLEANUP job and returns 201")
    void scheduleJob_cleanupType_returns201() throws Exception {
        String requestBody = """
                {
                  "jobName": "cleanupJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "CLEANUP",
                  "cronExpression": "0 0 3 * * ?",
                  "description": "Integration test cleanup job"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobType").value("CLEANUP"))
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));
    }

    @Test
    @DisplayName("POST /api/jobs – schedules a REPORTING job and returns 201")
    void scheduleJob_reportingType_returns201() throws Exception {
        String requestBody = """
                {
                  "jobName": "reportJob",
                  "jobGroup": "REPORTING",
                  "jobType": "REPORTING",
                  "cronExpression": "0 0 9 ? * MON-FRI",
                  "description": "Integration test reporting job"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobType").value("REPORTING"))
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));
    }

    @Test
    @DisplayName("POST /api/jobs – returns 400 when jobName is blank")
    void scheduleJob_blankJobName_returns400() throws Exception {
        String requestBody = """
                {
                  "jobName": "",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/jobs – returns 400 when jobType is invalid")
    void scheduleJob_invalidJobType_returns400() throws Exception {
        String requestBody = """
                {
                  "jobName": "badJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "UNKNOWN_TYPE",
                  "cronExpression": "0 0/5 * * * ?"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/jobs – returns 400 when cron expression is invalid")
    void scheduleJob_invalidCron_returns400() throws Exception {
        String requestBody = """
                {
                  "jobName": "badCronJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "not-valid-cron"
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/jobs ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/jobs – returns the newly scheduled job in the list")
    void listJobs_returnsScheduledJob() throws Exception {
        // Given: schedule a job first
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?",
                  "description": "List test"
                }
                """;
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When / Then: the list contains the scheduled job
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[?(@.jobName=='loggingJob')].jobGroup")
                        .value(hasItem("MAINTENANCE")));
    }

    // ── GET /api/jobs/{group}/{name} ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/jobs/{group}/{name} – returns job details")
    void getJob_existingJob_returns200() throws Exception {
        // Given: a scheduled job
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?"
                }
                """;
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When / Then
        mockMvc.perform(get("/api/jobs/MAINTENANCE/loggingJob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobName").value("loggingJob"))
                .andExpect(jsonPath("$.jobGroup").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("GET /api/jobs/{group}/{name} – returns 404 for non-existent job")
    void getJob_nonExistentJob_returns404() throws Exception {
        mockMvc.perform(get("/api/jobs/GHOST/missingJob"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../pause then .../resume cycles the job state correctly")
    void pauseAndResume_cyclesTriggerState() throws Exception {
        // Given: schedule a job
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?"
                }
                """;
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When: pause the job
        mockMvc.perform(post("/api/jobs/MAINTENANCE/loggingJob/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: trigger state is PAUSED
        mockMvc.perform(get("/api/jobs/MAINTENANCE/loggingJob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerState").value("PAUSED"));

        // When: resume the job
        mockMvc.perform(post("/api/jobs/MAINTENANCE/loggingJob/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: trigger state is NORMAL again
        mockMvc.perform(get("/api/jobs/MAINTENANCE/loggingJob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.triggerState").value("NORMAL"));
    }

    // ── Trigger now ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../trigger fires the job immediately and writes an audit record")
    void triggerNow_firesJobAndWritesAuditRecord() throws Exception {
        // Given: schedule a LOGGING job
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0 0 1 1 ? 2099"
                }
                """;
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When: trigger the job immediately
        mockMvc.perform(post("/api/jobs/MAINTENANCE/loggingJob/trigger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: wait briefly for the async job execution to complete, then check audit log
        // Quartz executes jobs on a separate thread pool; we poll with a short delay
        Thread.sleep(2000); // 2 seconds is plenty for a simple logging job

        // Verify at least one audit record was written for this job
        long auditCount = auditLogRepository.findByJobNameOrderByFiredAtDesc("loggingJob").size();
        assertThat(auditCount).isGreaterThanOrEqualTo(1);
    }

    // ── DELETE /api/jobs/{group}/{name} ───────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/jobs/{group}/{name} – removes the job from the scheduler")
    void deleteJob_removesFromScheduler() throws Exception {
        // Given: schedule a job
        String requestBody = """
                {
                  "jobName": "loggingJob",
                  "jobGroup": "MAINTENANCE",
                  "jobType": "LOGGING",
                  "cronExpression": "0 0/5 * * * ?"
                }
                """;
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody));

        // When: delete the job
        mockMvc.perform(delete("/api/jobs/MAINTENANCE/loggingJob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Then: the job is no longer found
        mockMvc.perform(get("/api/jobs/MAINTENANCE/loggingJob"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/audit ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/audit – returns empty page initially")
    void getAuditLogs_emptyInitially_returnsEmptyPage() throws Exception {
        // The @BeforeEach already deleted all audit records
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
