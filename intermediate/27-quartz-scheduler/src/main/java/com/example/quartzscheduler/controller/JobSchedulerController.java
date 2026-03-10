package com.example.quartzscheduler.controller;

import com.example.quartzscheduler.dto.ApiResponse;
import com.example.quartzscheduler.dto.JobInfoResponse;
import com.example.quartzscheduler.dto.ScheduleJobRequest;
import com.example.quartzscheduler.service.JobSchedulerService;
import jakarta.validation.Valid;
import org.quartz.SchedulerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the Quartz job-management API.
 *
 * <h2>Endpoints summary</h2>
 * <pre>
 * POST   /api/jobs                          – schedule a new job
 * GET    /api/jobs                          – list all scheduled jobs
 * GET    /api/jobs/{group}/{name}           – get a single job
 * POST   /api/jobs/{group}/{name}/pause     – pause a job
 * POST   /api/jobs/{group}/{name}/resume    – resume a paused job
 * POST   /api/jobs/{group}/{name}/trigger   – fire a job immediately
 * DELETE /api/jobs/{group}/{name}           – delete a job
 * </pre>
 *
 * <h2>Error handling</h2>
 * <p>All {@link SchedulerException} and {@link IllegalArgumentException} errors
 * bubble up to {@link GlobalExceptionHandler} which converts them to structured
 * JSON error responses.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobSchedulerController {

    /**
     * Service that wraps all interactions with the Quartz {@link org.quartz.Scheduler}.
     */
    private final JobSchedulerService schedulerService;

    public JobSchedulerController(JobSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    // ── POST /api/jobs ────────────────────────────────────────────────────────────

    /**
     * Schedules a new Quartz job based on the supplied request body.
     *
     * <p>Returns {@code 201 Created} with the job info, or {@code 400 Bad Request}
     * if the request fails Bean Validation or the cron expression is invalid.
     *
     * @param request validated request body (jobName, jobGroup, jobType, cronExpression)
     * @return HTTP 201 with {@link JobInfoResponse} body
     * @throws SchedulerException if Quartz fails to persist the job
     */
    @PostMapping
    public ResponseEntity<JobInfoResponse> scheduleJob(
            @Valid @RequestBody ScheduleJobRequest request) throws SchedulerException {

        JobInfoResponse response = schedulerService.scheduleJob(request);
        // 201 Created – a new resource (job) has been created in the scheduler
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/jobs ─────────────────────────────────────────────────────────────

    /**
     * Returns a list of all jobs currently registered with the Quartz scheduler.
     *
     * <p>Each entry includes the job identity, type, cron expression, trigger state,
     * and next/previous fire times.
     *
     * @return HTTP 200 with list of {@link JobInfoResponse}
     * @throws SchedulerException if Quartz fails to read the job store
     */
    @GetMapping
    public ResponseEntity<List<JobInfoResponse>> listJobs() throws SchedulerException {
        return ResponseEntity.ok(schedulerService.listAllJobs());
    }

    // ── GET /api/jobs/{group}/{name} ──────────────────────────────────────────────

    /**
     * Returns detailed information about a single job identified by group and name.
     *
     * @param group Quartz job group (path variable)
     * @param name  Quartz job name (path variable)
     * @return HTTP 200 with {@link JobInfoResponse}, or 404 if not found
     * @throws SchedulerException if Quartz fails to read the job store
     */
    @GetMapping("/{group}/{name}")
    public ResponseEntity<JobInfoResponse> getJob(
            @PathVariable String group,
            @PathVariable String name) throws SchedulerException {

        JobInfoResponse response = schedulerService.getJob(name, group);
        return ResponseEntity.ok(response);
    }

    // ── POST /api/jobs/{group}/{name}/pause ───────────────────────────────────────

    /**
     * Pauses all triggers for the named job.  The job is not deleted; it can
     * be resumed at any time via the {@code /resume} endpoint.
     *
     * @param group Quartz job group
     * @param name  Quartz job name
     * @return HTTP 200 with success message
     * @throws SchedulerException if Quartz fails
     */
    @PostMapping("/{group}/{name}/pause")
    public ResponseEntity<ApiResponse> pauseJob(
            @PathVariable String group,
            @PathVariable String name) throws SchedulerException {

        schedulerService.pauseJob(name, group);
        return ResponseEntity.ok(ApiResponse.ok("Job paused: " + group + "/" + name));
    }

    // ── POST /api/jobs/{group}/{name}/resume ──────────────────────────────────────

    /**
     * Resumes a previously paused job, restoring it to normal scheduling.
     *
     * @param group Quartz job group
     * @param name  Quartz job name
     * @return HTTP 200 with success message
     * @throws SchedulerException if Quartz fails
     */
    @PostMapping("/{group}/{name}/resume")
    public ResponseEntity<ApiResponse> resumeJob(
            @PathVariable String group,
            @PathVariable String name) throws SchedulerException {

        schedulerService.resumeJob(name, group);
        return ResponseEntity.ok(ApiResponse.ok("Job resumed: " + group + "/" + name));
    }

    // ── POST /api/jobs/{group}/{name}/trigger ─────────────────────────────────────

    /**
     * Fires the named job immediately, outside of its regular cron schedule.
     *
     * <p>Useful for manual ad-hoc executions, e.g. "run the cleanup job right now"
     * without waiting for the next scheduled time.
     *
     * @param group Quartz job group
     * @param name  Quartz job name
     * @return HTTP 200 with success message
     * @throws SchedulerException if Quartz fails
     */
    @PostMapping("/{group}/{name}/trigger")
    public ResponseEntity<ApiResponse> triggerNow(
            @PathVariable String group,
            @PathVariable String name) throws SchedulerException {

        schedulerService.triggerJobNow(name, group);
        return ResponseEntity.ok(ApiResponse.ok("Job triggered immediately: " + group + "/" + name));
    }

    // ── DELETE /api/jobs/{group}/{name} ───────────────────────────────────────────

    /**
     * Permanently removes the named job and all its triggers from the scheduler.
     *
     * @param group Quartz job group
     * @param name  Quartz job name
     * @return HTTP 200 with success message
     * @throws SchedulerException if Quartz fails
     */
    @DeleteMapping("/{group}/{name}")
    public ResponseEntity<ApiResponse> deleteJob(
            @PathVariable String group,
            @PathVariable String name) throws SchedulerException {

        schedulerService.deleteJob(name, group);
        return ResponseEntity.ok(ApiResponse.ok("Job deleted: " + group + "/" + name));
    }
}
