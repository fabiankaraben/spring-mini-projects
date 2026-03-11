package com.example.dynamicscheduling.controller;

import com.example.dynamicscheduling.dto.ApiResponse;
import com.example.dynamicscheduling.dto.CreateTaskRequest;
import com.example.dynamicscheduling.dto.TaskStatusResponse;
import com.example.dynamicscheduling.dto.UpdateIntervalRequest;
import com.example.dynamicscheduling.service.TaskManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the dynamic scheduling management API.
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 * POST   /api/tasks                          – create a new scheduled task
 * GET    /api/tasks                          – list all registered tasks
 * GET    /api/tasks/{taskName}               – get a single task's status
 * PATCH  /api/tasks/{taskName}/interval      – update the execution interval (live)
 * POST   /api/tasks/{taskName}/enable        – re-enable a disabled task
 * POST   /api/tasks/{taskName}/disable       – pause a running task
 * DELETE /api/tasks/{taskName}               – permanently delete a task
 * </pre>
 *
 * <h2>Key design point</h2>
 * <p>The {@code PATCH /interval} endpoint is the demonstration of dynamic
 * scheduling: calling it updates both the database and the in-memory
 * {@link com.example.dynamicscheduling.scheduling.DynamicTaskRegistry}.  The
 * scheduler's custom trigger reads the registry on every evaluation, so the
 * new interval takes effect on the very next execution cycle without any
 * restart.
 *
 * <h2>Error handling</h2>
 * <p>All {@link IllegalArgumentException} errors bubble up to
 * {@link GlobalExceptionHandler} which converts them to structured JSON
 * error responses with appropriate HTTP status codes.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    /** Service that manages task lifecycle operations. */
    private final TaskManagementService taskManagementService;

    public TaskController(TaskManagementService taskManagementService) {
        this.taskManagementService = taskManagementService;
    }

    // ── POST /api/tasks ───────────────────────────────────────────────────────────

    /**
     * Creates a new dynamic scheduled task and registers it with the scheduler.
     *
     * <p>If {@code enabled=true} in the request, the task starts executing
     * immediately after creation.  If {@code enabled=false} it is persisted
     * in the database in a paused state and can be started via {@code /enable}.
     *
     * @param request validated request body (taskName, description, intervalMs, enabled)
     * @return HTTP 201 Created with {@link TaskStatusResponse} body
     */
    @PostMapping
    public ResponseEntity<TaskStatusResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request) {

        TaskStatusResponse response = taskManagementService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/tasks ────────────────────────────────────────────────────────────

    /**
     * Returns the current status of all registered tasks.
     *
     * @return HTTP 200 with list of {@link TaskStatusResponse}
     */
    @GetMapping
    public ResponseEntity<List<TaskStatusResponse>> listTasks() {
        return ResponseEntity.ok(taskManagementService.listTasks());
    }

    // ── GET /api/tasks/{taskName} ─────────────────────────────────────────────────

    /**
     * Returns the current status of a single task identified by name.
     *
     * @param taskName unique logical name of the task (path variable)
     * @return HTTP 200 with {@link TaskStatusResponse}, or 404 if not found
     */
    @GetMapping("/{taskName}")
    public ResponseEntity<TaskStatusResponse> getTask(@PathVariable String taskName) {
        return ResponseEntity.ok(taskManagementService.getTask(taskName));
    }

    // ── PATCH /api/tasks/{taskName}/interval ──────────────────────────────────────

    /**
     * Updates the execution interval of a running task without restarting it.
     *
     * <p>This is the core demonstration of <em>dynamic scheduling</em>: the new
     * interval is applied immediately via the in-memory registry and takes effect
     * on the very next trigger evaluation cycle.
     *
     * @param taskName unique logical name of the task (path variable)
     * @param request  request body containing the new interval in milliseconds
     * @return HTTP 200 with updated {@link TaskStatusResponse}
     */
    @PatchMapping("/{taskName}/interval")
    public ResponseEntity<TaskStatusResponse> updateInterval(
            @PathVariable String taskName,
            @Valid @RequestBody UpdateIntervalRequest request) {

        TaskStatusResponse response = taskManagementService.updateInterval(taskName, request);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/tasks/{taskName}/enable ─────────────────────────────────────────

    /**
     * Re-enables a previously disabled task, scheduling it immediately.
     *
     * @param taskName unique logical name of the task (path variable)
     * @return HTTP 200 with updated {@link TaskStatusResponse}
     */
    @PostMapping("/{taskName}/enable")
    public ResponseEntity<TaskStatusResponse> enableTask(@PathVariable String taskName) {
        TaskStatusResponse response = taskManagementService.enableTask(taskName);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/tasks/{taskName}/disable ────────────────────────────────────────

    /**
     * Pauses a running task by cancelling its scheduled future.
     *
     * <p>The task configuration is preserved in the database; the task can be
     * re-enabled at any time.
     *
     * @param taskName unique logical name of the task (path variable)
     * @return HTTP 200 with updated {@link TaskStatusResponse}
     */
    @PostMapping("/{taskName}/disable")
    public ResponseEntity<TaskStatusResponse> disableTask(@PathVariable String taskName) {
        TaskStatusResponse response = taskManagementService.disableTask(taskName);
        return ResponseEntity.ok(response);
    }

    // ── DELETE /api/tasks/{taskName} ──────────────────────────────────────────────

    /**
     * Permanently deletes a task: cancels its future, removes it from the registry,
     * and deletes its database record.
     *
     * @param taskName unique logical name of the task (path variable)
     * @return HTTP 200 with success message
     */
    @DeleteMapping("/{taskName}")
    public ResponseEntity<ApiResponse> deleteTask(@PathVariable String taskName) {
        taskManagementService.deleteTask(taskName);
        return ResponseEntity.ok(ApiResponse.ok("Task deleted: " + taskName));
    }
}
