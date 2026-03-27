package com.example.distributedlock.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.distributedlock.domain.TaskResult;
import com.example.distributedlock.domain.TaskService;
import com.example.distributedlock.domain.TaskStatus;
import com.example.distributedlock.web.dto.TaskRequest;
import com.example.distributedlock.web.dto.TaskResponse;

import jakarta.validation.Valid;

/**
 * REST controller for the Task API.
 *
 * <p>Exposes a single endpoint that accepts task submissions and attempts to
 * execute each task while holding a Redis distributed lock.  The HTTP status
 * code communicates the lock outcome to the caller:
 *
 * <ul>
 *   <li><strong>200 OK</strong>         — lock acquired, task COMPLETED.</li>
 *   <li><strong>409 Conflict</strong>   — lock NOT acquired (SKIPPED); another node
 *                                         is already executing the same task.</li>
 *   <li><strong>500 Internal Error</strong> — thread was INTERRUPTED during
 *                                             lock acquisition or processing.</li>
 * </ul>
 *
 * <h2>Demonstrating distributed locking</h2>
 * Send two requests with the same {@code taskKey} at the same time:
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"demo","payload":"first"}' &amp;
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"demo","payload":"second"}'
 * </pre>
 * The first will respond with 200 (COMPLETED) and the second with 409 (SKIPPED).
 *
 * <p>Requests with <em>different</em> {@code taskKey} values always run concurrently
 * because they use different Redis lock keys:
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"job-A","payload":"data"}' &amp;
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"job-B","payload":"data"}'
 * </pre>
 * Both will respond 200 (COMPLETED) simultaneously.
 */
@RestController
@RequestMapping("/api/tasks")
@Validated
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    /**
     * Constructs the controller with its required service dependency.
     *
     * @param taskService the service that handles distributed-lock-protected task execution
     */
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Submits a task for execution under a distributed Redis lock.
     *
     * <p>The request body is validated with Bean Validation before the method is
     * invoked.  Invalid requests (blank fields, size violations) are rejected
     * automatically with 400 Bad Request by Spring's exception handler.
     *
     * @param request the task submission containing the lock key and payload
     * @return a {@link ResponseEntity} whose status reflects the lock outcome:
     *         <ul>
     *           <li>200 — COMPLETED</li>
     *           <li>409 — SKIPPED (lock not available)</li>
     *           <li>500 — INTERRUPTED</li>
     *         </ul>
     */
    @PostMapping
    public ResponseEntity<TaskResponse> submitTask(@Valid @RequestBody TaskRequest request) {
        log.info("Received task submission: taskKey='{}', payload='{}'",
                request.taskKey(), request.payload());

        // Delegate all locking and processing logic to the domain service.
        // The controller only translates the domain result to an HTTP response.
        TaskResult result = taskService.executeWithLock(request.taskKey(), request.payload());

        // Map the domain TaskStatus to an appropriate HTTP status code.
        HttpStatus httpStatus = switch (result.status()) {
            // Happy path: lock was acquired and the task ran to completion.
            case COMPLETED -> HttpStatus.OK;

            // Lock not available: another node is handling the same task right now.
            // 409 Conflict signals the caller that the resource (task) is busy.
            case SKIPPED -> HttpStatus.CONFLICT;

            // Unexpected interruption: use 500 to indicate a server-side problem.
            case INTERRUPTED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        log.info("Task '{}' result: status={}, elapsed={}ms",
                result.taskKey(), result.status(), result.elapsedMs());

        return ResponseEntity
                .status(httpStatus)
                .body(TaskResponse.from(result));
    }

    /**
     * Health/smoke endpoint to confirm the application is running.
     *
     * <p>Note: the primary health endpoint is at {@code /actuator/health}.
     * This endpoint exists to make manual smoke-testing with curl easier
     * without having to navigate to the actuator URL.
     */
    @org.springframework.web.bind.annotation.GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Distributed Lock Redis application is running.");
    }
}
