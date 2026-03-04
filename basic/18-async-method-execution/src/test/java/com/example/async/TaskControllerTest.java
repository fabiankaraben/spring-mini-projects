package com.example.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void testExecuteTaskNonBlocking() throws Exception {
        // Mocking the TaskService to return a CompletableFuture immediately
        String taskId = "123";
        String expectedResult = "Task " + taskId + " completed successfully";
        when(taskService.executeLongRunningTask(anyString()))
                .thenReturn(CompletableFuture.completedFuture(expectedResult));

        // When triggering an async return, Spring MVC initiates an async dispatch over
        // the Servlet API
        MvcResult mvcResult = mockMvc.perform(get("/api/tasks/{taskId}/non-blocking", taskId))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Simulate web container resuming request thread after future completion
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(expectedResult));
    }

    @Test
    void testExecuteTaskFireAndForget() throws Exception {
        // Fire & forget just invokes the service method, so we still mock its mock
        // return.
        String taskId = "456";
        when(taskService.executeLongRunningTask(anyString()))
                .thenReturn(CompletableFuture.completedFuture("Ignored")); // Return doesn't matter

        // This endpoint should return 202 Accepted synchronously
        mockMvc.perform(get("/api/tasks/{taskId}/fire-and-forget", taskId))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Task " + taskId + " has been accepted for background processing."));
    }
}
