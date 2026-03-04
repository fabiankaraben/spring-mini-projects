package com.example.scheduledtask.controller;

import com.example.scheduledtask.service.JobStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Test for JobStatusController using @WebMvcTest.
 * This slice test explicitly loads only the web layer components of the
 * application.
 */
@WebMvcTest(JobStatusController.class)
class JobStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobStatusService jobStatusService;

    @Test
    void testGetStatusReturnsCorrectData() throws Exception {
        // Arrange
        int expectedCount = 5;
        LocalDateTime expectedTime = LocalDateTime.of(2025, 1, 1, 12, 0, 0);

        when(jobStatusService.getExecutionCount()).thenReturn(expectedCount);
        when(jobStatusService.getLastExecutionTime()).thenReturn(expectedTime);

        // Act & Assert
        // Perform a GET request to the endpoint and verify the JSON response
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExecutions").value(expectedCount))
                .andExpect(jsonPath("$.lastExecutionTime").value(expectedTime.toString()));
    }
}
