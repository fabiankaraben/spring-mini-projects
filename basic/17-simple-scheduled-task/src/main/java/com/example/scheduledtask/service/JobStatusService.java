package com.example.scheduledtask.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to hold the state of the scheduled job executions.
 * This class simply stores the number of times the job has run
 * and the timestamp of the last execution.
 */
@Service
public class JobStatusService {

    private final AtomicInteger executionCount = new AtomicInteger(0);
    private LocalDateTime lastExecutionTime;

    /**
     * Records a new execution of the scheduled job.
     */
    public void recordExecution() {
        executionCount.incrementAndGet();
        lastExecutionTime = LocalDateTime.now();
    }

    /**
     * Gets the total number of times the job has been executed.
     * 
     * @return count
     */
    public int getExecutionCount() {
        return executionCount.get();
    }

    /**
     * Gets the time the job was last executed.
     * 
     * @return time of last execution, or null if never executed
     */
    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }
}
