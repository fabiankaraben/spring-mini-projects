package com.example.batchcsvtodb.integration;

import com.example.batchcsvtodb.model.Employee;
import com.example.batchcsvtodb.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration test for the {@code importEmployeesJob} batch job.
 *
 * <p>This test class:
 * <ul>
 *   <li>Spins up a real PostgreSQL database in Docker via Testcontainers.</li>
 *   <li>Starts the full Spring application context ({@code @SpringBootTest}).</li>
 *   <li>Runs Flyway migrations to create the real schema.</li>
 *   <li>Launches the batch job and verifies that valid CSV rows are persisted and
 *       invalid rows are skipped.</li>
 * </ul>
 *
 * <p>The {@code integration-test} profile activates {@code application-integration-test.yml}
 * which overrides the default {@code application.yml} with settings appropriate for the
 * Testcontainers database instance.
 *
 * <p><strong>Testcontainers Docker API version note:</strong> Docker Desktop 29+ rejects
 * the legacy v1.24 API endpoint. The {@code docker-java.properties} and
 * {@code testcontainers.properties} files in {@code src/test/resources} set
 * {@code api.version=1.44} to fix this.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("ImportEmployeesJob – integration tests")
class ImportEmployeesJobIntegrationTest {

    /**
     * Shared PostgreSQL container for all test methods in this class.
     *
     * <p>{@code @Container} makes Testcontainers manage the container lifecycle.
     * {@code static} means one container is reused for all tests (faster than
     * starting a new one per test method).
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("batchdb_test")
            .withUsername("batch_test")
            .withPassword("batch_test");

    /**
     * Injects the Testcontainers PostgreSQL URL into the Spring context
     * <em>before</em> the application context is started. This replaces the
     * datasource URL defined in {@code application.yml} with the dynamic port
     * assigned by Testcontainers.
     *
     * <p>{@code @DynamicPropertySource} is the recommended way to wire
     * Testcontainers datasource properties in Spring Boot 2.2.6+.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** The batch job under test – auto-configured by Spring Batch. */
    @Autowired
    private Job importEmployeesJob;

    /**
     * The {@code JobLauncher} that submits job executions.
     * In test mode it runs synchronously (blocks until the job completes).
     */
    @Autowired
    private JobLauncher jobLauncher;

    /** Repository used to assert the state of the employees table after the job runs. */
    @Autowired
    private EmployeeRepository employeeRepository;

    /**
     * Clears the employees table before each test to ensure test isolation.
     *
     * <p>This is important because Spring Batch meta-data tables track previously
     * completed jobs. Without clearing the employees table each test would see
     * rows from previous runs.
     */
    @BeforeEach
    void cleanDatabase() {
        employeeRepository.deleteAll();
    }

    // ── Core batch job tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("job should complete with COMPLETED status")
    void importJob_completesSuccessfully() throws Exception {
        // Arrange – unique run.id ensures Spring Batch allows re-running the same job
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();

        // Act – run the job synchronously
        JobExecution execution = jobLauncher.run(importEmployeesJob, params);

        // Assert – job must finish with COMPLETED status (not FAILED)
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("job should persist all valid CSV rows to the database")
    void importJob_persistsValidRows() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // The sample employees.csv has 20 data rows but 3 are invalid:
        //   row 16: INVALID_ROW – blank lastName, invalid email, negative salary, bad date
        //   row 17: peter.garcia@... – blank email field
        //   row 18: quinn.lopez@... – blank firstName field
        // So 17 valid rows should be persisted.
        List<Employee> employees = employeeRepository.findAll();
        assertThat(employees).hasSize(17);
    }

    @Test
    @DisplayName("job should skip invalid rows and not persist them")
    void importJob_skipsInvalidRows() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // The row with email 'not-an-email' (the INVALID_ROW) must NOT be persisted
        assertThat(employeeRepository.existsByEmail("not-an-email")).isFalse();

        // Peter Garcia row has blank email – should not be in the database
        // We verify by checking total count; only 17 valid rows should exist
        assertThat(employeeRepository.count()).isEqualTo(17);
    }

    @Test
    @DisplayName("job should correctly store first and last name")
    void importJob_storesNamesCorrectly() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // Assert – look up a specific employee and verify their name
        var alice = employeeRepository.findByEmail("alice.johnson@example.com");
        assertThat(alice).isPresent();
        assertThat(alice.get().getFirstName()).isEqualTo("Alice");
        assertThat(alice.get().getLastName()).isEqualTo("Johnson");
    }

    @Test
    @DisplayName("job should correctly store department and salary")
    void importJob_storesDepartmentAndSalary() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // Assert – Kate Thomas has the highest salary in the CSV (110000.00)
        var kate = employeeRepository.findByEmail("kate.thomas@example.com");
        assertThat(kate).isPresent();
        assertThat(kate.get().getDepartment()).isEqualTo("Engineering");
        assertThat(kate.get().getSalary()).isEqualByComparingTo("110000.00");
    }

    @Test
    @DisplayName("job should correctly parse and store hire date")
    void importJob_storesHireDateCorrectly() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // Assert – Alice's hire_date in the CSV is "2021-03-15"
        var alice = employeeRepository.findByEmail("alice.johnson@example.com");
        assertThat(alice).isPresent();
        assertThat(alice.get().getHireDate()).isEqualTo("2021-03-15");
    }

    @Test
    @DisplayName("repository findByDepartment should return only Engineering employees")
    void findByDepartment_returnsOnlyMatchingEmployees() throws Exception {
        // Arrange – run the import job first to populate the table
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(importEmployeesJob, params);

        // Act – query by department
        List<Employee> engineers = employeeRepository.findByDepartment("Engineering");

        // Assert – from the CSV: Alice, Bob, Eva, Irene, Kate, Noah, Rachel = 7 engineers
        assertThat(engineers).hasSize(7);
        // All returned employees must belong to Engineering
        assertThat(engineers).allMatch(e -> "Engineering".equals(e.getDepartment()));
    }

    @Test
    @DisplayName("step execution should report correct read and write counts")
    void importJob_stepExecutionCounts() throws Exception {
        // Act
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(importEmployeesJob, params);

        // Assert – verify step-level counters
        var stepExecution = execution.getStepExecutions().iterator().next();

        // 20 rows in the CSV (excluding header) → 20 reads
        assertThat(stepExecution.getReadCount()).isEqualTo(20);
        // 17 valid rows → 17 writes
        assertThat(stepExecution.getWriteCount()).isEqualTo(17);
        // 3 invalid rows → 3 filtered (processor returned null → filter, not skip)
        assertThat(stepExecution.getFilterCount()).isEqualTo(3);
    }
}
