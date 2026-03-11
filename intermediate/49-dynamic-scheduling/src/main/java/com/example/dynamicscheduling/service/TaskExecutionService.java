package com.example.dynamicscheduling.service;

import com.example.dynamicscheduling.model.TaskExecutionLog;
import com.example.dynamicscheduling.repository.TaskConfigRepository;
import com.example.dynamicscheduling.repository.TaskExecutionLogRepository;
import com.example.dynamicscheduling.scheduling.DynamicTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Executes the business logic for each named background task and records
 * the outcome in the {@code task_execution_log} table.
 *
 * <h2>Design</h2>
 * <p>Each scheduled task is just a named unit of work.  This service acts as
 * the dispatcher: given a task name it performs the appropriate simulation
 * and then writes a {@link TaskExecutionLog} row regardless of success or
 * failure.
 *
 * <p>In a real application the task logic would interact with databases,
 * external APIs, file systems, etc.  Here it is simulated with a short
 * {@link Thread#sleep} to represent work being done.
 *
 * <h2>Enabled check</h2>
 * <p>Before running, this service checks the {@link DynamicTaskRegistry} to
 * confirm the task is still enabled.  This guard catches the window between
 * a task being disabled via the REST API and its scheduled future being cancelled.
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    /** Registry providing live enabled/interval values. */
    private final DynamicTaskRegistry registry;

    /** Repository to persist execution log entries. */
    private final TaskExecutionLogRepository executionLogRepository;

    /** Repository used to look up the current interval snapshot for log entries. */
    private final TaskConfigRepository taskConfigRepository;

    public TaskExecutionService(DynamicTaskRegistry registry,
                                TaskExecutionLogRepository executionLogRepository,
                                TaskConfigRepository taskConfigRepository) {
        this.registry               = registry;
        this.executionLogRepository = executionLogRepository;
        this.taskConfigRepository   = taskConfigRepository;
    }

    // ── Task dispatcher ───────────────────────────────────────────────────────────

    /**
     * Executes the named task and writes an execution log record.
     *
     * <p>If the task has been disabled in the registry since the trigger last
     * evaluated, this method returns immediately without performing any work or
     * writing a log entry.
     *
     * @param taskName the unique logical name of the task to execute
     */
    public void executeTask(String taskName) {
        // Guard: skip execution if the task has been disabled since scheduling
        if (!registry.isEnabled(taskName, true)) {
            log.debug("Task='{}' is disabled – skipping execution", taskName);
            return;
        }

        // Record the start time to compute wall-clock duration
        Instant start = Instant.now();
        long intervalMsSnapshot = registry.getInterval(taskName, 5000L);

        log.info("Executing task='{}'", taskName);

        try {
            // Dispatch to the appropriate task logic based on task name
            dispatchTaskLogic(taskName);

            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.info("Task='{}' completed in {} ms", taskName, durationMs);

            // Persist a SUCCESS execution log entry
            executionLogRepository.save(
                new TaskExecutionLog(taskName, start, durationMs, intervalMsSnapshot)
            );

        } catch (Exception ex) {
            long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();
            log.error("Task='{}' failed after {} ms: {}", taskName, durationMs, ex.getMessage());

            // Persist a FAILURE execution log entry (never suppress exceptions silently)
            executionLogRepository.save(
                new TaskExecutionLog(taskName, start, durationMs, intervalMsSnapshot,
                                     ex.getMessage())
            );
        }
    }

    // ── Task logic implementations ────────────────────────────────────────────────

    /**
     * Dispatches execution to a task-specific implementation based on the task name.
     *
     * <p>Each {@code case} represents a distinct background task type.  Adding a new
     * task type requires only a new {@code case} here and a new configuration row
     * in the database.
     *
     * @param taskName the unique logical name of the task
     * @throws InterruptedException if the simulated work is interrupted
     */
    private void dispatchTaskLogic(String taskName) throws InterruptedException {
        switch (taskName) {
            case "heartbeat"   -> runHeartbeatTask();
            case "report"      -> runReportTask();
            case "cleanup"     -> runCleanupTask();
            case "data-sync"   -> runDataSyncTask();
            default            -> runGenericTask(taskName);
        }
    }

    /**
     * Heartbeat task – simulates a lightweight health-check ping.
     * Designed to run frequently (e.g. every 3 seconds) to demonstrate
     * high-frequency scheduling.
     *
     * @throws InterruptedException if interrupted during the simulated work
     */
    private void runHeartbeatTask() throws InterruptedException {
        log.debug("Heartbeat: sending health-check ping");
        // Simulate a very fast health-check (~50 ms)
        Thread.sleep(50);
        log.debug("Heartbeat: ping OK");
    }

    /**
     * Report generation task – simulates aggregating data and producing a report.
     * Designed to run less frequently (e.g. every 30 seconds) to demonstrate
     * low-frequency scheduling.
     *
     * @throws InterruptedException if interrupted during the simulated work
     */
    private void runReportTask() throws InterruptedException {
        log.debug("Report: aggregating data for periodic report");
        // Simulate moderate processing time (~200 ms)
        Thread.sleep(200);
        log.debug("Report: report generated successfully");
    }

    /**
     * Data cleanup task – simulates removing stale records from a database or
     * file system.  Typical use case for a nightly scheduled job.
     *
     * @throws InterruptedException if interrupted during the simulated work
     */
    private void runCleanupTask() throws InterruptedException {
        log.debug("Cleanup: scanning for stale records");
        // Simulate a moderate scan (~150 ms)
        Thread.sleep(150);
        log.debug("Cleanup: stale records removed");
    }

    /**
     * Data sync task – simulates pulling data from an external source and
     * reconciling it with the local store.
     *
     * @throws InterruptedException if interrupted during the simulated work
     */
    private void runDataSyncTask() throws InterruptedException {
        log.debug("DataSync: fetching data from external source");
        // Simulate network I/O (~300 ms)
        Thread.sleep(300);
        log.debug("DataSync: synchronisation complete");
    }

    /**
     * Generic task handler for task names that don't match a specific
     * implementation.  Performs a short sleep to simulate some work.
     *
     * @param taskName the task name (used for logging)
     * @throws InterruptedException if interrupted during the simulated work
     */
    private void runGenericTask(String taskName) throws InterruptedException {
        log.debug("GenericTask='{}': executing simulated work", taskName);
        // Simulate a generic short task (~100 ms)
        Thread.sleep(100);
        log.debug("GenericTask='{}': completed", taskName);
    }
}
