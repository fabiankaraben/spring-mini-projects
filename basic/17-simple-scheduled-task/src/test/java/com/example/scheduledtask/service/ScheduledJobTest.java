package com.example.scheduledtask.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for ScheduledJob using JUnit 5 and Mockito.
 * Uses {@link MockitoExtension} to auto-initialize mocks.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledJobTest {

    @Mock
    private JobStatusService jobStatusService;

    @InjectMocks
    private ScheduledJob scheduledJob;

    @Test
    void testRunPeriodicTaskRecordsExecution() {
        // Arrange
        // When getExecutionCount is called on the mock, return 1 as a dummy value after
        // invocation
        when(jobStatusService.getExecutionCount()).thenReturn(1);

        // Act
        // Call the scheduled method directly (simulate execution event)
        scheduledJob.runPeriodicTask();

        // Assert
        // Verify that recordExecution was called exactly once on the mocked service
        verify(jobStatusService, times(1)).recordExecution();
        // Verify that getExecutionCount was also called once during the logging step
        verify(jobStatusService, times(1)).getExecutionCount();
    }
}
