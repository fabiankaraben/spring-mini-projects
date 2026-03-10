package com.example.quartzscheduler.job;

import com.example.quartzscheduler.model.JobAuditLog;
import com.example.quartzscheduler.repository.JobAuditLogRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * A simple Quartz job that logs a message and records an audit entry.
 *
 * <h2>Purpose</h2>
 * <p>This job demonstrates the simplest possible Quartz job: it prints a
 * timestamped message to the log and writes a {@link JobAuditLog} row to
 * PostgreSQL so operators can see when it last ran.
 *
 * <h2>Key annotations</h2>
 * <ul>
 *   <li>{@link DisallowConcurrentExecution} – Quartz will not start a new
 *       instance of this job while a previous instance is still running.
 *       This prevents overlapping executions which could cause duplicate
 *       audit rows when the cron interval is shorter than the execution time.</li>
 *   <li>{@link Component} – registers the bean in the Spring context so that
 *       {@link org.springframework.scheduling.quartz.SpringBeanJobFactory}
 *       can inject Spring-managed dependencies (the repository).</li>
 * </ul>
 *
 * <h2>Extending {@code QuartzJobBean}</h2>
 * <p>Spring's {@link QuartzJobBean} acts as an adapter between Quartz and
 * Spring. It calls {@link #executeInternal} after injecting any bean properties
 * declared via {@link org.quartz.JobDataMap} or Spring dependency injection.
 */
@Component
@DisallowConcurrentExecution
public class SampleLoggingJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(SampleLoggingJob.class);

    /**
     * Repository injected by Spring's {@code SpringBeanJobFactory}.
     * Quartz normally creates job instances itself; the factory overrides that
     * behaviour to delegate to the Spring application context, enabling full DI.
     */
    private final JobAuditLogRepository auditLogRepository;

    public SampleLoggingJob(JobAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Core execution logic invoked by Quartz on each trigger fire.
     *
     * <p>The method:
     * <ol>
     *   <li>Records the start time.</li>
     *   <li>Performs the "work" (here just a log statement).</li>
     *   <li>Persists a {@link JobAuditLog} row with duration and outcome.</li>
     * </ol>
     *
     * @param context Quartz execution context – carries the trigger, job detail,
     *                job data map, and scheduler reference.
     * @throws JobExecutionException if the job should be re-fired or the
     *                               scheduler should un-schedule it on failure.
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        // Capture when the job started for duration calculation
        Instant start = Instant.now();

        // Extract Quartz identity fields from the job detail
        String jobName  = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String jobClass = getClass().getName();

        log.info("[SampleLoggingJob] Executing job={} group={}", jobName, jobGroup);

        try {
            // ── Simulate some work ────────────────────────────────────────────────
            // In a real application this could be a health check, a data sync,
            // sending a notification, or any periodic background task.
            log.info("[SampleLoggingJob] Performing periodic log message at {}", Instant.now());

            // ── Persist SUCCESS audit record ──────────────────────────────────────
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "SUCCESS");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage("Logging job completed successfully.");
            auditLogRepository.save(entry);

            log.info("[SampleLoggingJob] Completed in {} ms", entry.getDurationMs());

        } catch (Exception ex) {
            // ── Persist FAILED audit record ───────────────────────────────────────
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "FAILED");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage(ex.getMessage());
            auditLogRepository.save(entry);

            log.error("[SampleLoggingJob] Failed: {}", ex.getMessage(), ex);
            // Wrap in JobExecutionException so Quartz can handle retry/unschedule logic
            throw new JobExecutionException(ex);
        }
    }
}
