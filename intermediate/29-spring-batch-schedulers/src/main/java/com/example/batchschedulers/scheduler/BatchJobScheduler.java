package com.example.batchschedulers.scheduler;

import com.example.batchschedulers.model.JobAuditLog;
import com.example.batchschedulers.repository.JobAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Spring Scheduler component that triggers Spring Batch jobs on a periodic schedule.
 *
 * <p>This class demonstrates combining {@code @EnableScheduling} (declared on
 * {@link com.example.batchschedulers.SpringBatchSchedulersApplication}) with
 * {@code @Scheduled} method annotations to run batch jobs at configurable intervals.
 *
 * <p>Each scheduled method follows the same pattern:
 * <ol>
 *   <li>Create a {@link JobAuditLog} record and save it with status "RUNNING".</li>
 *   <li>Build {@link JobParameters} with a unique {@code run.id} (timestamp) so
 *       Spring Batch allows re-running the same job multiple times.</li>
 *   <li>Launch the job via {@link JobLauncher} (synchronous by default in Spring Boot).</li>
 *   <li>Update the audit log record with the final status and finish time.</li>
 * </ol>
 *
 * <p>Schedule configuration:
 * <ul>
 *   <li>{@code priceRefreshJob}      – every 30 seconds (configurable via
 *       {@code batch.schedule.price-refresh-cron})</li>
 *   <li>{@code inventoryAuditJob}    – every 60 seconds / cron (configurable via
 *       {@code batch.schedule.inventory-audit-cron})</li>
 *   <li>{@code reportGenerationJob}  – every 2 minutes (configurable via
 *       {@code batch.schedule.report-generation-cron})</li>
 * </ul>
 *
 * <p><strong>Thread safety note:</strong> {@code @Scheduled} methods run on a
 * single-threaded {@code ThreadPoolTaskScheduler} by default. Each method is
 * therefore called sequentially on that thread. If you need parallel execution,
 * configure a multi-threaded {@code TaskScheduler} bean.
 */
@Component
public class BatchJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchJobScheduler.class);

    /**
     * The Spring Batch {@code JobLauncher}. In Spring Boot's default configuration
     * the launcher runs jobs <em>synchronously</em> — it blocks until the job
     * completes before returning. This is the simplest and safest model for
     * scheduled batch jobs because the next scheduled tick cannot overlap with an
     * ongoing execution.
     */
    private final JobLauncher jobLauncher;

    /** The priceRefreshJob bean defined in {@code PriceRefreshJobConfig}. */
    private final Job priceRefreshJob;

    /** The inventoryAuditJob bean defined in {@code InventoryAuditJobConfig}. */
    private final Job inventoryAuditJob;

    /** The reportGenerationJob bean defined in {@code ReportGenerationJobConfig}. */
    private final Job reportGenerationJob;

    /** Repository for persisting job execution audit records. */
    private final JobAuditLogRepository auditLogRepository;

    public BatchJobScheduler(JobLauncher jobLauncher,
                             @Qualifier("priceRefreshJob") Job priceRefreshJob,
                             @Qualifier("inventoryAuditJob") Job inventoryAuditJob,
                             @Qualifier("reportGenerationJob") Job reportGenerationJob,
                             JobAuditLogRepository auditLogRepository) {
        this.jobLauncher = jobLauncher;
        this.priceRefreshJob = priceRefreshJob;
        this.inventoryAuditJob = inventoryAuditJob;
        this.reportGenerationJob = reportGenerationJob;
        this.auditLogRepository = auditLogRepository;
    }

    // ── Scheduled triggers ────────────────────────────────────────────────────

    /**
     * Triggers the {@code priceRefreshJob} on the configured cron schedule.
     *
     * <p>Default schedule: every 30 seconds (cron: {@code "0/30 * * * * *"}).
     * Override with the {@code batch.schedule.price-refresh-cron} property,
     * e.g. {@code "0 0/5 * * * *"} for every 5 minutes.
     *
     * <p>The {@code fixedDelay} vs {@code cron} distinction matters:
     * {@code fixedDelay} waits N ms <em>after</em> the previous run completes;
     * {@code cron} fires at wall-clock times regardless of previous run duration.
     * We use cron here so the schedule is predictable and configurable.
     */
    @Scheduled(cron = "${batch.schedule.price-refresh-cron:0/30 * * * * *}")
    public void schedulePriceRefreshJob() {
        runJob(priceRefreshJob, "SCHEDULED");
    }

    /**
     * Triggers the {@code inventoryAuditJob} on the configured cron schedule.
     *
     * <p>Default schedule: every 60 seconds (cron: {@code "0 * * * * *"}).
     * Override with {@code batch.schedule.inventory-audit-cron}.
     */
    @Scheduled(cron = "${batch.schedule.inventory-audit-cron:0 * * * * *}")
    public void scheduleInventoryAuditJob() {
        runJob(inventoryAuditJob, "SCHEDULED");
    }

    /**
     * Triggers the {@code reportGenerationJob} on the configured cron schedule.
     *
     * <p>Default schedule: every 2 minutes (cron: {@code "0 0/2 * * * *"}).
     * Override with {@code batch.schedule.report-generation-cron}.
     */
    @Scheduled(cron = "${batch.schedule.report-generation-cron:0 0/2 * * * *}")
    public void scheduleReportGenerationJob() {
        runJob(reportGenerationJob, "SCHEDULED");
    }

    // ── Shared job execution helper ───────────────────────────────────────────

    /**
     * Launches the given batch job, records the execution in the audit log, and
     * returns the {@link JobExecution} result.
     *
     * <p>This method is package-visible (not private) so that the
     * {@link com.example.batchschedulers.controller.BatchJobController} can
     * reuse it for manual "MANUAL" trigger invocations.
     *
     * @param job         the Spring Batch {@link Job} to run
     * @param triggerType "SCHEDULED" or "MANUAL"
     * @return the {@link JobExecution} produced by the launcher, or {@code null}
     *         if a launch exception occurred (details are recorded in the audit log)
     */
    public JobExecution runJob(Job job, String triggerType) {
        String jobName = job.getName();
        log.info("Triggering job '{}' (trigger={})", jobName, triggerType);

        // Create and persist the audit log entry with status RUNNING
        JobAuditLog auditLog = new JobAuditLog(jobName, triggerType);
        auditLog = auditLogRepository.save(auditLog);

        try {
            // Build unique JobParameters using the current timestamp as run.id.
            // Spring Batch rejects a second launch of the same job with identical
            // parameters if the first run COMPLETED; the unique run.id prevents this.
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            // Launch the job (synchronous – blocks until the job finishes)
            JobExecution execution = jobLauncher.run(job, params);

            // Update audit log with execution details
            auditLog.setJobExecutionId(execution.getId());
            auditLog.setFinishedAt(LocalDateTime.now());
            auditLog.setStatus(execution.getStatus().name());
            auditLogRepository.save(auditLog);

            log.info("Job '{}' finished with status {} (executionId={})",
                    jobName, execution.getStatus(), execution.getId());
            return execution;

        } catch (Exception e) {
            // Record the error in the audit log so it's visible via the REST API
            auditLog.setFinishedAt(LocalDateTime.now());
            auditLog.setStatus("LAUNCH_ERROR");
            auditLog.setMessage(e.getMessage());
            auditLogRepository.save(auditLog);

            log.error("Failed to launch job '{}': {}", jobName, e.getMessage(), e);
            return null;
        }
    }
}
