package com.example.scheduledtask.controller;

import com.example.scheduledtask.service.JobStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST controller to verify the status of the scheduled job executions.
 */
@RestController
@RequestMapping("/api/status")
public class JobStatusController {

    private final JobStatusService jobStatusService;

    // Dependeny injection via constructor
    public JobStatusController(JobStatusService jobStatusService) {
        this.jobStatusService = jobStatusService;
    }

    /**
     * Endpoint to fetch the execution stats of the background job.
     * 
     * @return map with the status.
     */
    @GetMapping
    public Map<String, Object> getStatus() {
        int count = jobStatusService.getExecutionCount();
        LocalDateTime lastExec = jobStatusService.getLastExecutionTime();

        return Map.of(
                "totalExecutions", count,
                "lastExecutionTime", lastExec != null ? lastExec.toString() : "Never executed");
    }
}
