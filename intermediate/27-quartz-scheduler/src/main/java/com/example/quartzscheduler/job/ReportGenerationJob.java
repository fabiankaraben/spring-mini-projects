package com.example.quartzscheduler.job;

import com.example.quartzscheduler.model.JobAuditLog;
import com.example.quartzscheduler.repository.JobAuditLogRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Quartz job that simulates a periodic report-generation operation.
 *
 * <h2>Real-world analogy</h2>
 * <p>In production this job would query a database, compute aggregates, and
 * write the results to a file, email, or dashboard endpoint.  Here it
 * demonstrates the pattern by summarising the total number of audit-log entries
 * currently stored, which mirrors the kind of metrics report a real scheduler
 * would produce.
 *
 * <h2>Stateful job – {@link PersistJobDataAfterExecution}</h2>
 * <p>This job is annotated with {@link PersistJobDataAfterExecution}, which
 * tells Quartz to update the persisted {@link org.quartz.JobDataMap} after each
 * execution.  The job uses this to track two counters:
 * <ul>
 *   <li>{@code runCount} – how many times the job has run since it was created.</li>
 *   <li>{@code lastReportTimestamp} – the ISO-8601 timestamp of the most recent run.</li>
 * </ul>
 * These values are visible in the REST API response and are stored durably in
 * the Quartz JDBC job-store tables, so they survive application restarts.
 *
 * <h2>Why combine with {@link DisallowConcurrentExecution}?</h2>
 * <p>When a job is stateful (persists its data map), Quartz requires
 * {@link DisallowConcurrentExecution} to prevent two instances from
 * simultaneously writing to the same data-map row and producing a race condition.
 */
@Component
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class ReportGenerationJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationJob.class);

    /** JobDataMap key that stores the cumulative run count. */
    public static final String KEY_RUN_COUNT = "runCount";

    /** JobDataMap key that stores the ISO-8601 timestamp of the last run. */
    public static final String KEY_LAST_RUN = "lastReportTimestamp";

    /** ISO-8601 date-time formatter for the last-run timestamp (UTC). */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    private final JobAuditLogRepository auditLogRepository;

    public ReportGenerationJob(JobAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Generates a summary report by querying the audit-log table.
     *
     * <p>Steps:
     * <ol>
     *   <li>Increment the {@code runCount} counter in the job data map.</li>
     *   <li>Query the total number of audit-log entries from the database.</li>
     *   <li>Log the report summary.</li>
     *   <li>Store the current timestamp as {@code lastReportTimestamp}.</li>
     *   <li>Persist a SUCCESS/FAILED audit entry for this report run itself.</li>
     * </ol>
     *
     * <p>Because {@link PersistJobDataAfterExecution} is present, Quartz will
     * write the updated data map back to the {@code QRTZ_JOB_DETAILS} table after
     * this method returns, making {@code runCount} and {@code lastReportTimestamp}
     * durable across restarts.
     *
     * @param context Quartz execution context.
     * @throws JobExecutionException on unexpected errors.
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Instant start = Instant.now();
        String jobName  = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String jobClass = getClass().getName();

        // Increment the persistent run counter stored in the job data map
        int runCount = context.getJobDetail().getJobDataMap().getIntValue(KEY_RUN_COUNT) + 1;
        context.getJobDetail().getJobDataMap().put(KEY_RUN_COUNT, runCount);

        log.info("[ReportGenerationJob] Starting report run #{}, job={}/{}",
                runCount, jobGroup, jobName);

        try {
            // ── Generate report ───────────────────────────────────────────────────
            // Query the total number of audit entries stored in the database
            long totalAuditRecords = auditLogRepository.count();

            // In a real application you would compute more interesting metrics:
            // - count by status (SUCCESS vs FAILED)
            // - average job duration per job type
            // - jobs that have not run in the expected window (missed-fire detection)
            String reportSummary = String.format(
                    "Report #%d | Total audit records: %d | Generated at: %s",
                    runCount, totalAuditRecords, FORMATTER.format(start));

            log.info("[ReportGenerationJob] {}", reportSummary);

            // Store the last run timestamp in the data map so it survives restarts
            context.getJobDetail().getJobDataMap().put(KEY_LAST_RUN, FORMATTER.format(start));

            // ── Persist SUCCESS audit record ──────────────────────────────────────
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "SUCCESS");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage(reportSummary);
            auditLogRepository.save(entry);

            log.info("[ReportGenerationJob] Completed in {} ms", entry.getDurationMs());

        } catch (Exception ex) {
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "FAILED");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage(ex.getMessage());
            auditLogRepository.save(entry);

            log.error("[ReportGenerationJob] Failed: {}", ex.getMessage(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
