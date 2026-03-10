package com.example.quartzscheduler.controller;

import com.example.quartzscheduler.model.JobAuditLog;
import com.example.quartzscheduler.repository.JobAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for querying the job-execution audit log.
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 * GET /api/audit                           – paginated list of all audit records
 * GET /api/audit/{jobName}                 – all records for a specific job name
 * GET /api/audit/{jobGroup}/{jobName}      – records for a specific job name + group
 * </pre>
 *
 * <p>The audit log is written by each job implementation (see
 * {@link com.example.quartzscheduler.job.SampleLoggingJob},
 * {@link com.example.quartzscheduler.job.DataCleanupJob},
 * {@link com.example.quartzscheduler.job.ReportGenerationJob}) after every
 * execution.  This controller exposes that history via a read-only REST API.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    /**
     * Spring Data JPA repository that provides read access to the audit table.
     * Injected by Spring's constructor injection mechanism.
     */
    private final JobAuditLogRepository auditLogRepository;

    public AuditLogController(JobAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // ── GET /api/audit ────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all job-execution audit records, newest first.
     *
     * <p>Pagination prevents overwhelming the response when the audit table
     * grows large.  The default page size is 20; clients may pass {@code ?page=}
     * and {@code ?size=} query parameters to navigate.
     *
     * @param page zero-based page index (default 0)
     * @param size number of records per page (default 20)
     * @return HTTP 200 with a Spring Data {@link Page} of {@link JobAuditLog}
     */
    @GetMapping
    public ResponseEntity<Page<JobAuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        // PageRequest encapsulates the page index and page size
        Pageable pageable = PageRequest.of(page, size);
        Page<JobAuditLog> auditPage = auditLogRepository.findAllByOrderByFiredAtDesc(pageable);
        return ResponseEntity.ok(auditPage);
    }

    // ── GET /api/audit/{jobName} ──────────────────────────────────────────────────

    /**
     * Returns all audit records for a job name across all groups, newest first.
     *
     * <p>Useful when a job name is unique across groups and you just want to see
     * all executions for that job regardless of group.
     *
     * @param jobName the Quartz job name to filter by
     * @return HTTP 200 with a list of {@link JobAuditLog}
     */
    @GetMapping("/{jobName}")
    public ResponseEntity<List<JobAuditLog>> getAuditLogsByJobName(
            @PathVariable String jobName) {

        List<JobAuditLog> records =
                auditLogRepository.findByJobNameOrderByFiredAtDesc(jobName);
        return ResponseEntity.ok(records);
    }

    // ── GET /api/audit/{jobGroup}/{jobName} ───────────────────────────────────────

    /**
     * Returns all audit records for a specific job name within a specific group,
     * newest first.
     *
     * <p>Use this endpoint when the same job name appears in multiple groups and
     * you need to disambiguate.
     *
     * @param jobGroup the Quartz job group
     * @param jobName  the Quartz job name
     * @return HTTP 200 with a list of {@link JobAuditLog}
     */
    @GetMapping("/{jobGroup}/{jobName}")
    public ResponseEntity<List<JobAuditLog>> getAuditLogsByGroupAndName(
            @PathVariable String jobGroup,
            @PathVariable String jobName) {

        List<JobAuditLog> records =
                auditLogRepository.findByJobNameAndJobGroupOrderByFiredAtDesc(jobName, jobGroup);
        return ResponseEntity.ok(records);
    }
}
