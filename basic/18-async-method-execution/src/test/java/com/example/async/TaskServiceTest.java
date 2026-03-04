package com.example.async;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the service layer logic.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @InjectMocks
    private TaskService taskService;

    @Test
    void testExecuteLongRunningTask() throws ExecutionException, InterruptedException {
        String taskId = "test";

        // As tests don't load async proxies out-of-the-box in unit scope,
        // this will run synchronously on the test thread, achieving code coverage.
        CompletableFuture<String> future = taskService.executeLongRunningTask(taskId);

        String result = future.get(); // Should be immediately accessible because we didn't inject proxy

        assertEquals("Task " + taskId + " completed successfully", result);
        assertTrue(future.isDone());
    }
}
