package com.example.dynamicscheduling.controller;

import com.example.dynamicscheduling.model.TaskExecutionLog;
import com.example.dynamicscheduling.repository.TaskExecutionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for querying task execution logs.
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 * GET /api/logs                     – paginated list of all execution logs (newest first)
 * GET /api/logs/{taskName}          – paginated logs for a specific task
 * </pre>
 *
 * <p>Pagination defaults to page 0, size 20.  Callers can override with
 * {@code ?page=1&size=50} query parameters.
 */
@RestController
@RequestMapping("/api/logs")
public class ExecutionLogController {

    /** Repository for reading execution log records from PostgreSQL. */
    private final TaskExecutionLogRepository logRepository;

    public ExecutionLogController(TaskExecutionLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    // ── GET /api/logs ─────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of all task execution log entries ordered by
     * fired time descending (newest first).
     *
     * @param page zero-based page index (default 0)
     * @param size page size, number of records per page (default 20)
     * @return HTTP 200 with a {@link Page} of {@link TaskExecutionLog}
     */
    @GetMapping
    public ResponseEntity<Page<TaskExecutionLog>> getAllLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(logRepository.findAllByOrderByFiredAtDesc(pageable));
    }

    // ── GET /api/logs/{taskName} ──────────────────────────────────────────────────

    /**
     * Returns a paginated list of execution log entries for a specific task,
     * ordered by fired time descending (newest first).
     *
     * @param taskName the unique logical task name to filter by (path variable)
     * @param page     zero-based page index (default 0)
     * @param size     page size, number of records per page (default 20)
     * @return HTTP 200 with a {@link Page} of {@link TaskExecutionLog}
     */
    @GetMapping("/{taskName}")
    public ResponseEntity<Page<TaskExecutionLog>> getLogsForTask(
            @PathVariable String taskName,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
            logRepository.findByTaskNameOrderByFiredAtDesc(taskName, pageable)
        );
    }
}
