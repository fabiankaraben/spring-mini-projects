package com.example.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service class simulating long-running tasks.
 */
@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    /**
     * Executes a background task asynchronously.
     * The @Async annotation with the bean name "taskExecutor" tells Spring
     * to execute this method in the custom thread pool we defined.
     *
     * @param taskId the ID of the task
     * @return a CompletableFuture containing the result of the task
     */
    @Async("taskExecutor")
    public CompletableFuture<String> executeLongRunningTask(String taskId) {
        logger.info("Task {} started on thread: {}", taskId, Thread.currentThread().getName());
        try {
            // Simulate a long-running operation (e.g., calling an external API, complex
            // calculation)
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Task {} was interrupted", taskId, e);
            return CompletableFuture.completedFuture("Task " + taskId + " failed");
        }

        String result = "Task " + taskId + " completed successfully";
        logger.info("Task {} finished on thread: {}", taskId, Thread.currentThread().getName());

        // Return the result wrapped in a CompletableFuture, since @Async methods
        // that return a value must return a Future subtype.
        return CompletableFuture.completedFuture(result);
    }
}
