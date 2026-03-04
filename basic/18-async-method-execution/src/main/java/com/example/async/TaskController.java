package com.example.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller to trigger background tasks.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Endpoint to trigger an asynchronous task.
     * Although we are returning a CompletableFuture that waits for the service's
     * background thread
     * to complete, Spring MVC async support uses this so the HTTP worker thread is
     * not blocked
     * waiting for the underlying async process to finish.
     *
     * @param taskId ID of task
     * @return a CompletableFuture resolving into a ResponseEntity
     */
    @GetMapping("/{taskId}/non-blocking")
    public CompletableFuture<ResponseEntity<String>> executeTaskNonBlocking(@PathVariable String taskId) {
        logger.info("Received request for task {} on thread: {}", taskId, Thread.currentThread().getName());

        // Spring MVC automatically handles the CompletableFuture response type:
        // freeing up the web container thread while waiting for the business thread to
        // finish.
        return taskService.executeLongRunningTask(taskId)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * Endpoint to trigger an asynchronous task in a true "fire and forget" manner.
     * The client receives an immediate response while the task runs quietly in the
     * background.
     *
     * @param taskId ID of task
     * @return a 202 Accepted response
     */
    @GetMapping("/{taskId}/fire-and-forget")
    public ResponseEntity<String> executeTaskFireAndForget(@PathVariable String taskId) {
        logger.info("Received fire-and-forget request for task {} on thread: {}", taskId,
                Thread.currentThread().getName());

        // Result is ignored in this endpoint to demonstrate immediate return
        taskService.executeLongRunningTask(taskId);

        return ResponseEntity.accepted().body("Task " + taskId + " has been accepted for background processing.");
    }
}
