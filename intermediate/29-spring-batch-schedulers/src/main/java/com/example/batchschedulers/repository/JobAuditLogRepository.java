package com.example.batchschedulers.repository;

import com.example.batchschedulers.model.JobAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link JobAuditLog} entities.
 *
 * <p>Provides access to the audit log of all batch job executions.
 * The {@code BatchJobController} uses this repository to serve the
 * {@code GET /api/batch/audit-logs} endpoint.
 */
@Repository
public interface JobAuditLogRepository extends JpaRepository<JobAuditLog, Long> {

    /**
     * Returns all audit log entries for a specific job, ordered by start time
     * descending (most recent first).
     *
     * @param jobName the job name (e.g. "priceRefreshJob")
     * @return list of audit logs for the given job
     */
    List<JobAuditLog> findByJobNameOrderByStartedAtDesc(String jobName);

    /**
     * Returns all audit log entries with a specific status, ordered by start time
     * descending (most recent first).
     *
     * @param status the status string (e.g. "COMPLETED", "FAILED")
     * @return list of matching audit log entries
     */
    List<JobAuditLog> findByStatusOrderByStartedAtDesc(String status);
}
