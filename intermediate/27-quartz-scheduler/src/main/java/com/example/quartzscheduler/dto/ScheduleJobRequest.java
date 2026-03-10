package com.example.quartzscheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for the "schedule a new job" endpoint.
 *
 * <p>Clients POST this payload to {@code /api/jobs} to register a new Quartz
 * job with the scheduler.  The three supported job types are:
 * <ul>
 *   <li>{@code LOGGING}  – {@link com.example.quartzscheduler.job.SampleLoggingJob}</li>
 *   <li>{@code CLEANUP}  – {@link com.example.quartzscheduler.job.DataCleanupJob}</li>
 *   <li>{@code REPORTING} – {@link com.example.quartzscheduler.job.ReportGenerationJob}</li>
 * </ul>
 *
 * <h2>Cron expression format</h2>
 * <p>The {@code cronExpression} must follow the Quartz cron format which has
 * <strong>six or seven</strong> fields (seconds, minutes, hours, day-of-month,
 * month, day-of-week, [year]).  This differs from the UNIX/Spring {@code @Scheduled}
 * format which has only five fields.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code "0 * * * * ?"} – every minute</li>
 *   <li>{@code "0 0 * * * ?"} – every hour</li>
 *   <li>{@code "0 0 3 * * ?"} – daily at 03:00 UTC</li>
 *   <li>{@code "0 0 9 ? * MON-FRI"} – weekdays at 09:00 UTC</li>
 * </ul>
 *
 * @param jobName        unique name for this job within the group
 * @param jobGroup       logical group for related jobs (e.g. "MAINTENANCE")
 * @param jobType        one of LOGGING, CLEANUP, or REPORTING
 * @param cronExpression Quartz cron expression controlling the trigger schedule
 * @param description    optional human-readable description stored in the job detail
 */
public record ScheduleJobRequest(

        /**
         * Unique name identifying this job within its group.
         * Maps directly to {@link org.quartz.JobKey#getName()}.
         */
        @NotBlank(message = "jobName must not be blank")
        String jobName,

        /**
         * Logical grouping for related jobs.
         * Maps to {@link org.quartz.JobKey#getGroup()}.
         */
        @NotBlank(message = "jobGroup must not be blank")
        String jobGroup,

        /**
         * Discriminator for the Quartz job class to instantiate.
         * Accepted values: LOGGING, CLEANUP, REPORTING.
         */
        @NotBlank(message = "jobType must not be blank")
        @Pattern(regexp = "LOGGING|CLEANUP|REPORTING",
                message = "jobType must be one of: LOGGING, CLEANUP, REPORTING")
        String jobType,

        /**
         * Quartz cron expression (6 or 7 fields).
         * Example: "0 0/5 * * * ?" fires every 5 minutes.
         */
        @NotBlank(message = "cronExpression must not be blank")
        String cronExpression,

        /**
         * Optional free-text description stored with the Quartz job detail.
         * Visible in the job-list API response.
         */
        String description
) {}
