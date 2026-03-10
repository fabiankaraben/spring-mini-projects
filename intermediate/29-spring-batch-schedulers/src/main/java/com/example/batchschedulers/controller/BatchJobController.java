package com.example.batchschedulers.controller;

import com.example.batchschedulers.model.JobAuditLog;
import com.example.batchschedulers.model.Product;
import com.example.batchschedulers.model.ProductReport;
import com.example.batchschedulers.repository.JobAuditLogRepository;
import com.example.batchschedulers.repository.ProductRepository;
import com.example.batchschedulers.repository.ProductReportRepository;
import com.example.batchschedulers.scheduler.BatchJobScheduler;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing endpoints for managing and observing the scheduled
 * Spring Batch jobs.
 *
 * <p>Base path: {@code /api/batch}
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>{@code POST /api/batch/jobs/{jobName}/run} – manually trigger a job</li>
 *   <li>{@code GET  /api/batch/audit-logs}          – list all audit log entries</li>
 *   <li>{@code GET  /api/batch/audit-logs/{jobName}} – audit logs for a specific job</li>
 *   <li>{@code GET  /api/batch/products}             – list all products</li>
 *   <li>{@code GET  /api/batch/products/low-stock}   – list low-stock products</li>
 *   <li>{@code GET  /api/batch/reports}              – list all generated reports</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

    private final BatchJobScheduler scheduler;
    private final Job priceRefreshJob;
    private final Job inventoryAuditJob;
    private final Job reportGenerationJob;
    private final JobAuditLogRepository auditLogRepository;
    private final ProductRepository productRepository;
    private final ProductReportRepository productReportRepository;

    public BatchJobController(BatchJobScheduler scheduler,
                              @Qualifier("priceRefreshJob") Job priceRefreshJob,
                              @Qualifier("inventoryAuditJob") Job inventoryAuditJob,
                              @Qualifier("reportGenerationJob") Job reportGenerationJob,
                              JobAuditLogRepository auditLogRepository,
                              ProductRepository productRepository,
                              ProductReportRepository productReportRepository) {
        this.scheduler = scheduler;
        this.priceRefreshJob = priceRefreshJob;
        this.inventoryAuditJob = inventoryAuditJob;
        this.reportGenerationJob = reportGenerationJob;
        this.auditLogRepository = auditLogRepository;
        this.productRepository = productRepository;
        this.productReportRepository = productReportRepository;
    }

    // ── Job trigger endpoints ─────────────────────────────────────────────────

    /**
     * Manually triggers a named batch job.
     *
     * <p>Supported job names:
     * <ul>
     *   <li>{@code priceRefreshJob}</li>
     *   <li>{@code inventoryAuditJob}</li>
     *   <li>{@code reportGenerationJob}</li>
     * </ul>
     *
     * <p>Example: {@code POST /api/batch/jobs/priceRefreshJob/run}
     *
     * @param jobName the logical job name
     * @return 200 with a summary map, or 400 for unknown job names
     */
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<Map<String, Object>> runJob(@PathVariable String jobName) {
        Job job = resolveJob(jobName);
        if (job == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown job: " + jobName,
                            "validJobs", List.of("priceRefreshJob", "inventoryAuditJob", "reportGenerationJob")));
        }

        // Delegate to the scheduler's shared execution helper with trigger type MANUAL
        JobExecution execution = scheduler.runJob(job, "MANUAL");
        if (execution == null) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Job launch failed – check audit logs for details"));
        }

        return ResponseEntity.ok(Map.of(
                "jobName", jobName,
                "executionId", execution.getId(),
                "status", execution.getStatus().name(),
                "startTime", execution.getStartTime() != null ? execution.getStartTime().toString() : "N/A",
                "endTime", execution.getEndTime() != null ? execution.getEndTime().toString() : "N/A"
        ));
    }

    // ── Audit log endpoints ───────────────────────────────────────────────────

    /**
     * Returns all job audit log entries, ordered by start time descending.
     *
     * <p>Example: {@code GET /api/batch/audit-logs}
     *
     * @return list of all {@link JobAuditLog} records
     */
    @GetMapping("/audit-logs")
    public List<JobAuditLog> getAllAuditLogs() {
        // Spring Data findAll returns by insertion order by default;
        // we sort in memory for simplicity in this demo
        return auditLogRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()))
                .toList();
    }

    /**
     * Returns audit log entries for a specific job, ordered by start time descending.
     *
     * <p>Example: {@code GET /api/batch/audit-logs/priceRefreshJob}
     *
     * @param jobName the job name to filter on
     * @return list of matching {@link JobAuditLog} records
     */
    @GetMapping("/audit-logs/{jobName}")
    public List<JobAuditLog> getAuditLogsByJob(@PathVariable String jobName) {
        return auditLogRepository.findByJobNameOrderByStartedAtDesc(jobName);
    }

    // ── Product query endpoints ───────────────────────────────────────────────

    /**
     * Returns all products in the catalog.
     *
     * <p>Example: {@code GET /api/batch/products}
     *
     * @return list of all {@link Product} records
     */
    @GetMapping("/products")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Returns only products that are currently flagged as low-stock.
     *
     * <p>Products are flagged by the {@code inventoryAuditJob} when their
     * {@code stockQuantity} falls below {@code Product.LOW_STOCK_THRESHOLD}.
     *
     * <p>Example: {@code GET /api/batch/products/low-stock}
     *
     * @return list of low-stock {@link Product} records
     */
    @GetMapping("/products/low-stock")
    public List<Product> getLowStockProducts() {
        return productRepository.findByLowStock(true);
    }

    /**
     * Returns products filtered by category.
     *
     * <p>Example: {@code GET /api/batch/products?category=Electronics}
     *
     * @param category the category to filter on
     * @return list of matching products
     */
    @GetMapping(value = "/products", params = "category")
    public List<Product> getProductsByCategory(@RequestParam String category) {
        return productRepository.findByCategory(category);
    }

    // ── Report endpoints ──────────────────────────────────────────────────────

    /**
     * Returns all generated product category reports, ordered by generation time
     * descending (most recent first).
     *
     * <p>Example: {@code GET /api/batch/reports}
     *
     * @return list of all {@link ProductReport} records
     */
    @GetMapping("/reports")
    public List<ProductReport> getAllReports() {
        return productReportRepository.findAll()
                .stream()
                .sorted((a, b) -> b.getGeneratedAt().compareTo(a.getGeneratedAt()))
                .toList();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Resolves a job name string to the corresponding {@link Job} bean.
     *
     * @param jobName the job name
     * @return the matching job, or {@code null} if the name is unknown
     */
    private Job resolveJob(String jobName) {
        return switch (jobName) {
            case "priceRefreshJob"     -> priceRefreshJob;
            case "inventoryAuditJob"   -> inventoryAuditJob;
            case "reportGenerationJob" -> reportGenerationJob;
            default                    -> null;
        };
    }
}
