package com.example.batchschedulers.integration;

import com.example.batchschedulers.model.JobAuditLog;
import com.example.batchschedulers.model.Product;
import com.example.batchschedulers.model.ProductReport;
import com.example.batchschedulers.repository.JobAuditLogRepository;
import com.example.batchschedulers.repository.ProductRepository;
import com.example.batchschedulers.repository.ProductReportRepository;
import com.example.batchschedulers.scheduler.BatchJobScheduler;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for all three scheduled Spring Batch jobs.
 *
 * <p>This test class:
 * <ul>
 *   <li>Spins up a real PostgreSQL database in Docker via Testcontainers.</li>
 *   <li>Starts the complete Spring application context ({@code @SpringBootTest}).</li>
 *   <li>Runs Flyway migrations to create the real schema (V1–V4).</li>
 *   <li>Verifies each job independently: {@code priceRefreshJob},
 *       {@code inventoryAuditJob}, and {@code reportGenerationJob}.</li>
 *   <li>Also tests the {@link BatchJobScheduler#runJob} helper used by both the
 *       scheduler and the REST controller.</li>
 * </ul>
 *
 * <p>The {@code integration-test} Spring profile (activated via
 * {@code @ActiveProfiles}) applies {@code application-integration-test.yml}
 * which:
 * <ul>
 *   <li>Disables all {@code @Scheduled} cron triggers (set to {@code "-"}) so
 *       background job executions do not interfere with test assertions.</li>
 *   <li>Sets the price-refresh adjustment factor to {@code 1.00} (no-op) so
 *       product prices remain stable and predictable for assertions.</li>
 * </ul>
 *
 * <p><strong>Docker API version note:</strong> Docker Desktop 29+ rejects
 * the legacy v1.24 API. The {@code docker-java.properties} and
 * {@code testcontainers.properties} files in {@code src/test/resources} set
 * {@code api.version=1.44} to fix this.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Batch Jobs – integration tests")
class BatchJobsIntegrationTest {

    /**
     * Shared PostgreSQL container for all test methods.
     * {@code static} = one container reused for the full test class (faster than
     * starting one per test method).
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("batchschedulers_test")
            .withUsername("batch_test")
            .withPassword("batch_test");

    /**
     * Injects the Testcontainers PostgreSQL JDBC URL into the Spring context
     * before it starts up. {@code @DynamicPropertySource} is the standard way
     * to wire Testcontainers datasource properties in Spring Boot 2.2.6+.
     */
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Auto-wired Spring beans ───────────────────────────────────────────────

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("priceRefreshJob")
    private Job priceRefreshJob;

    @Autowired
    @Qualifier("inventoryAuditJob")
    private Job inventoryAuditJob;

    @Autowired
    @Qualifier("reportGenerationJob")
    private Job reportGenerationJob;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductReportRepository productReportRepository;

    @Autowired
    private JobAuditLogRepository auditLogRepository;

    @Autowired
    private BatchJobScheduler batchJobScheduler;

    // ── Test setup ────────────────────────────────────────────────────────────

    /**
     * Resets database state before each test for full test isolation.
     * Reports and audit logs are cleared; products are re-seeded from scratch.
     *
     * <p>We must clear in dependency order to avoid FK violations:
     * product_reports → products, job_audit_logs (no FK dependencies).
     */
    @BeforeEach
    void resetDatabase() {
        // Clear derived data first
        productReportRepository.deleteAll();
        auditLogRepository.deleteAll();
        productRepository.deleteAll();

        // Re-seed a controlled set of products for each test
        productRepository.saveAll(List.of(
                new Product("Laptop Pro",     "Electronics", new BigDecimal("1000.00"), 25),
                new Product("Wireless Mouse", "Electronics", new BigDecimal("30.00"),   5),   // low stock
                new Product("Keyboard",       "Electronics", new BigDecimal("90.00"),   15),
                new Product("Clean Code",     "Books",       new BigDecimal("40.00"),   50),
                new Product("Spring Book",    "Books",       new BigDecimal("50.00"),   7),   // low stock
                new Product("Dev Hoodie",     "Clothing",    new BigDecimal("60.00"),   20),
                new Product("Coding Cap",     "Clothing",    new BigDecimal("20.00"),   3)    // low stock
        ));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds unique {@code JobParameters} to allow re-running jobs in tests.
     * Spring Batch would reject a second launch with identical parameters if the
     * first run COMPLETED; the {@code run.id} timestamp prevents this.
     */
    private JobParameters uniqueParams() {
        return new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
    }

    // ── priceRefreshJob tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("priceRefreshJob should complete with COMPLETED status")
    void priceRefreshJob_completesSuccessfully() throws Exception {
        JobExecution execution = jobLauncher.run(priceRefreshJob, uniqueParams());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("priceRefreshJob should process all products (read count = product count)")
    void priceRefreshJob_processesAllProducts() throws Exception {
        long productCount = productRepository.count();

        JobExecution execution = jobLauncher.run(priceRefreshJob, uniqueParams());

        // All products should be read and written (factor=1.00 → no actual price change)
        var stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo((int) productCount);
        assertThat(stepExecution.getWriteCount()).isEqualTo((int) productCount);
    }

    @Test
    @DisplayName("priceRefreshJob should update lastPriceUpdate on all products")
    void priceRefreshJob_updatesLastPriceUpdateTimestamp() throws Exception {
        // Verify all products have null lastPriceUpdate before the job
        assertThat(productRepository.findAll())
                .allMatch(p -> p.getLastPriceUpdate() == null);

        jobLauncher.run(priceRefreshJob, uniqueParams());

        // After the job, all products should have a non-null lastPriceUpdate
        assertThat(productRepository.findAll())
                .allMatch(p -> p.getLastPriceUpdate() != null);
    }

    // ── inventoryAuditJob tests ───────────────────────────────────────────────

    @Test
    @DisplayName("inventoryAuditJob should complete with COMPLETED status")
    void inventoryAuditJob_completesSuccessfully() throws Exception {
        JobExecution execution = jobLauncher.run(inventoryAuditJob, uniqueParams());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("inventoryAuditJob should flag low-stock products correctly")
    void inventoryAuditJob_flagsLowStockProducts() throws Exception {
        jobLauncher.run(inventoryAuditJob, uniqueParams());

        // 3 products have qty < 10: Wireless Mouse (5), Spring Book (7), Coding Cap (3)
        List<Product> lowStockProducts = productRepository.findByLowStock(true);
        assertThat(lowStockProducts).hasSize(3);

        // Verify specific low-stock products by name
        assertThat(lowStockProducts)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("Wireless Mouse", "Spring Book", "Coding Cap");
    }

    @Test
    @DisplayName("inventoryAuditJob should NOT flag products with sufficient stock")
    void inventoryAuditJob_doesNotFlagWellStockedProducts() throws Exception {
        jobLauncher.run(inventoryAuditJob, uniqueParams());

        // 4 products have qty >= 10: Laptop Pro, Keyboard, Clean Code, Dev Hoodie
        List<Product> adequateStockProducts = productRepository.findByLowStock(false);
        assertThat(adequateStockProducts).hasSize(4);
    }

    @Test
    @DisplayName("inventoryAuditJob should update lastAudited on all products")
    void inventoryAuditJob_updatesLastAuditedTimestamp() throws Exception {
        assertThat(productRepository.findAll())
                .allMatch(p -> p.getLastAudited() == null);

        jobLauncher.run(inventoryAuditJob, uniqueParams());

        assertThat(productRepository.findAll())
                .allMatch(p -> p.getLastAudited() != null);
    }

    @Test
    @DisplayName("inventoryAuditJob step should read and write all products")
    void inventoryAuditJob_stepCountsAreCorrect() throws Exception {
        long productCount = productRepository.count();

        JobExecution execution = jobLauncher.run(inventoryAuditJob, uniqueParams());

        var stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo((int) productCount);
        assertThat(stepExecution.getWriteCount()).isEqualTo((int) productCount);
    }

    // ── reportGenerationJob tests ─────────────────────────────────────────────

    @Test
    @DisplayName("reportGenerationJob should complete with COMPLETED status")
    void reportGenerationJob_completesSuccessfully() throws Exception {
        JobExecution execution = jobLauncher.run(reportGenerationJob, uniqueParams());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("reportGenerationJob should produce one report per category")
    void reportGenerationJob_producesOneReportPerCategory() throws Exception {
        jobLauncher.run(reportGenerationJob, uniqueParams());

        // 3 distinct categories: Electronics, Books, Clothing
        List<ProductReport> reports = productReportRepository.findAll();
        assertThat(reports).hasSize(3);

        assertThat(reports)
                .extracting(ProductReport::getCategory)
                .containsExactlyInAnyOrder("Electronics", "Books", "Clothing");
    }

    @Test
    @DisplayName("reportGenerationJob Electronics report should have correct product count")
    void reportGenerationJob_electronicsReport_hasCorrectCount() throws Exception {
        jobLauncher.run(reportGenerationJob, uniqueParams());

        // Electronics: Laptop Pro, Wireless Mouse, Keyboard = 3 products
        List<ProductReport> electronicsReports =
                productReportRepository.findByCategoryOrderByGeneratedAtDesc("Electronics");
        assertThat(electronicsReports).isNotEmpty();
        assertThat(electronicsReports.get(0).getProductCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("reportGenerationJob Electronics report should have correct total stock")
    void reportGenerationJob_electronicsReport_hasCorrectTotalStock() throws Exception {
        jobLauncher.run(reportGenerationJob, uniqueParams());

        // Electronics stock: 25 + 5 + 15 = 45
        List<ProductReport> electronicsReports =
                productReportRepository.findByCategoryOrderByGeneratedAtDesc("Electronics");
        assertThat(electronicsReports.get(0).getTotalStock()).isEqualTo(45);
    }

    @Test
    @DisplayName("reportGenerationJob should set generatedAt timestamp on reports")
    void reportGenerationJob_setsGeneratedAtTimestamp() throws Exception {
        jobLauncher.run(reportGenerationJob, uniqueParams());

        assertThat(productReportRepository.findAll())
                .allMatch(r -> r.getGeneratedAt() != null);
    }

    // ── BatchJobScheduler.runJob tests ────────────────────────────────────────

    @Test
    @DisplayName("BatchJobScheduler.runJob() should persist an audit log entry")
    void schedulerRunJob_persistsAuditLog() {
        assertThat(auditLogRepository.count()).isZero();

        // Use the scheduler's runJob helper (simulates what @Scheduled methods do)
        batchJobScheduler.runJob(priceRefreshJob, "MANUAL");

        // One audit log entry should have been created
        assertThat(auditLogRepository.count()).isEqualTo(1);
        JobAuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getJobName()).isEqualTo("priceRefreshJob");
        assertThat(log.getTriggerType()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("BatchJobScheduler.runJob() should record COMPLETED status in audit log")
    void schedulerRunJob_recordsCompletedStatus() {
        batchJobScheduler.runJob(priceRefreshJob, "SCHEDULED");

        List<JobAuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("BatchJobScheduler.runJob() should set jobExecutionId in audit log")
    void schedulerRunJob_setsJobExecutionId() {
        batchJobScheduler.runJob(inventoryAuditJob, "MANUAL");

        JobAuditLog log = auditLogRepository.findAll().get(0);
        // The execution ID is assigned by Spring Batch and must be non-null
        assertThat(log.getJobExecutionId()).isNotNull();
        assertThat(log.getJobExecutionId()).isPositive();
    }

    @Test
    @DisplayName("BatchJobScheduler.runJob() should set finishedAt in audit log")
    void schedulerRunJob_setsFinishedAt() {
        batchJobScheduler.runJob(reportGenerationJob, "MANUAL");

        JobAuditLog log = auditLogRepository.findAll().get(0);
        assertThat(log.getFinishedAt()).isNotNull();
        assertThat(log.getFinishedAt()).isAfterOrEqualTo(log.getStartedAt());
    }
}
