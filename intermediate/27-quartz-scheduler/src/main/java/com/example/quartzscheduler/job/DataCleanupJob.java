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
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Quartz job that simulates a periodic data-cleanup operation.
 *
 * <h2>Real-world analogy</h2>
 * <p>In production systems this job would delete expired records, purge stale
 * cache entries, archive old files, or compact database tables.  Here it
 * demonstrates the pattern by deleting {@link JobAuditLog} records that are
 * older than a configurable retention period (default: 30 days).  This is a
 * natural self-maintenance task for any audit-logging subsystem.
 *
 * <h2>Configuration via {@link org.quartz.JobDataMap}</h2>
 * <p>The retention period (in days) is passed to the job at scheduling time
 * via the job data map key {@code retentionDays}.  If the key is absent the
 * job falls back to the constant {@link #DEFAULT_RETENTION_DAYS}.
 * Reading from the data map rather than hard-coding the value makes the job
 * reusable and configurable without source changes.
 *
 * <h2>Why {@link DisallowConcurrentExecution}?</h2>
 * <p>A cleanup job that deletes rows must not overlap with itself – a second
 * instance could start deleting rows that the first instance has already
 * collected in a list, leading to "entity not found" errors or incorrect
 * counts.  The annotation tells Quartz to skip the trigger if the previous
 * execution has not finished yet.
 */
@Component
@DisallowConcurrentExecution
public class DataCleanupJob extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupJob.class);

    /** Default audit-log retention window when not overridden via JobDataMap. */
    public static final int DEFAULT_RETENTION_DAYS = 30;

    /**
     * JobDataMap key whose integer value sets how many days of audit records
     * should be retained.  Records older than this threshold are deleted.
     */
    public static final String KEY_RETENTION_DAYS = "retentionDays";

    private final JobAuditLogRepository auditLogRepository;

    public DataCleanupJob(JobAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Deletes audit-log records older than the configured retention window.
     *
     * <p>Steps:
     * <ol>
     *   <li>Read {@code retentionDays} from the job data map (or use the default).</li>
     *   <li>Compute the cutoff {@link Instant} = now − retentionDays.</li>
     *   <li>Load all records with {@code firedAt} before the cutoff and delete them.</li>
     *   <li>Persist a SUCCESS/FAILED audit entry for this cleanup run itself.</li>
     * </ol>
     *
     * @param context Quartz execution context providing the trigger and job detail.
     * @throws JobExecutionException on unexpected errors.
     */
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Instant start = Instant.now();
        String jobName  = context.getJobDetail().getKey().getName();
        String jobGroup = context.getJobDetail().getKey().getGroup();
        String jobClass = getClass().getName();

        // Read the retention period from the job data map; fall back to 30 days
        int retentionDays = context.getMergedJobDataMap().containsKey(KEY_RETENTION_DAYS)
                ? context.getMergedJobDataMap().getIntValue(KEY_RETENTION_DAYS)
                : DEFAULT_RETENTION_DAYS;

        log.info("[DataCleanupJob] Starting cleanup. retentionDays={}, job={}/{}",
                retentionDays, jobGroup, jobName);

        try {
            // Compute the oldest timestamp that should be kept
            Instant cutoff = start.minus(retentionDays, ChronoUnit.DAYS);

            // Find audit-log records older than the cutoff
            // (In a real system this would be a @Query with a DELETE; we load-and-delete
            //  here to keep the example readable without a custom JPQL delete query.)
            List<JobAuditLog> staleRecords = auditLogRepository.findAll().stream()
                    .filter(r -> r.getFiredAt() != null && r.getFiredAt().isBefore(cutoff))
                    .toList();

            int deletedCount = staleRecords.size();
            if (!staleRecords.isEmpty()) {
                auditLogRepository.deleteAll(staleRecords);
                log.info("[DataCleanupJob] Deleted {} stale audit record(s) older than {}",
                        deletedCount, cutoff);
            } else {
                log.info("[DataCleanupJob] No stale audit records found (cutoff={})", cutoff);
            }

            // Persist SUCCESS audit entry
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "SUCCESS");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage("Deleted " + deletedCount + " stale record(s) older than " + retentionDays + " days.");
            auditLogRepository.save(entry);

            log.info("[DataCleanupJob] Finished in {} ms", entry.getDurationMs());

        } catch (Exception ex) {
            Instant finish = Instant.now();
            JobAuditLog entry = new JobAuditLog(jobName, jobGroup, jobClass, start, "FAILED");
            entry.setFinishedAt(finish);
            entry.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
            entry.setMessage(ex.getMessage());
            auditLogRepository.save(entry);

            log.error("[DataCleanupJob] Failed: {}", ex.getMessage(), ex);
            throw new JobExecutionException(ex);
        }
    }
}
