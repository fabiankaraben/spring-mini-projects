package com.example.quartzscheduler.repository;

import com.example.quartzscheduler.model.JobAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link JobAuditLog} entities.
 *
 * <p>Spring Data automatically generates the implementation at startup;
 * no boilerplate DAO code is required.
 *
 * <p>The methods defined here are used by:
 * <ul>
 *   <li>Quartz job beans – to persist a new audit record on each execution.</li>
 *   <li>{@link com.example.quartzscheduler.controller.AuditLogController} – to
 *       expose audit history via the REST API.</li>
 * </ul>
 */
@Repository
public interface JobAuditLogRepository extends JpaRepository<JobAuditLog, Long> {

    /**
     * Returns all audit entries for a specific job name across all groups,
     * ordered from newest to oldest.
     *
     * @param jobName the Quartz job name to filter by
     * @return list of matching audit log entries, most recent first
     */
    List<JobAuditLog> findByJobNameOrderByFiredAtDesc(String jobName);

    /**
     * Returns all audit entries for a specific job within a specific group,
     * ordered from newest to oldest.
     *
     * @param jobName  the Quartz job name
     * @param jobGroup the Quartz job group
     * @return list of matching audit log entries, most recent first
     */
    List<JobAuditLog> findByJobNameAndJobGroupOrderByFiredAtDesc(String jobName, String jobGroup);

    /**
     * Returns a paginated view of all audit records ordered from newest to oldest.
     * Useful for large tables where returning all rows at once is not feasible.
     *
     * @param pageable paging and sorting parameters
     * @return a page of audit log entries
     */
    Page<JobAuditLog> findAllByOrderByFiredAtDesc(Pageable pageable);
}
