package com.example.scheduledtask.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Component responsible for executing scheduled tasks.
 * {@code @Component} registers it as a Spring Bean.
 */
@Component
public class ScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJob.class);

    private final JobStatusService jobStatusService;

    // Dependency Injection of the status service to record executions
    public ScheduledJob(JobStatusService jobStatusService) {
        this.jobStatusService = jobStatusService;
    }

    /**
     * Executes the task at a fixed rate.
     * The `fixedRate` parameter defines the interval in milliseconds
     * between the start of consecutive task executions.
     * Here, it runs every 5 seconds (5000 ms).
     */
    @Scheduled(fixedRate = 5000)
    public void runPeriodicTask() {
        log.info("Executing scheduled task at fixed rate...");
        // Record the execution in the status service
        jobStatusService.recordExecution();
        log.info("Task execution recorded. Total executions: {}", jobStatusService.getExecutionCount());
    }

    /**
     * Executes the task using a Cron expression.
     * The expression `0 * * * * *` means it will run at the start of every minute.
     * Notice: It is commented out so it doesn't spam logs, but serves as
     * educational purpose.
     */
    /*
     * @Scheduled(cron = "0 * * * * *")
     * public void runCronTask() {
     * log.info("Executing scheduled task using CRON Expression (Every minute)");
     * }
     */
}
