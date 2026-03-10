package com.example.quartzscheduler.dto;

/**
 * Response DTO that describes a single Quartz job as returned by the REST API.
 *
 * <p>This record is constructed from Quartz scheduler metadata (job detail +
 * trigger) and is serialised to JSON by Spring MVC's Jackson converter.
 *
 * <p>Fields map to Quartz concepts as follows:
 * <ul>
 *   <li>{@code jobName} / {@code jobGroup} → {@link org.quartz.JobKey}</li>
 *   <li>{@code triggerState} → {@link org.quartz.Trigger.TriggerState} converted to String</li>
 *   <li>{@code nextFireTime} → ISO-8601 UTC string from {@link org.quartz.Trigger#getNextFireTime()}</li>
 *   <li>{@code previousFireTime} → ISO-8601 UTC string from {@link org.quartz.Trigger#getPreviousFireTime()}</li>
 *   <li>{@code cronExpression} → extracted from a {@link org.quartz.CronTrigger}</li>
 * </ul>
 *
 * @param jobName          Quartz job name
 * @param jobGroup         Quartz job group
 * @param jobType          friendly job-type label (LOGGING, CLEANUP, REPORTING)
 * @param description      optional description stored in the job detail
 * @param triggerState     current state of the trigger (NORMAL, PAUSED, BLOCKED, etc.)
 * @param cronExpression   Quartz cron expression that controls the trigger schedule
 * @param nextFireTime     ISO-8601 UTC timestamp of the next scheduled execution, or null
 * @param previousFireTime ISO-8601 UTC timestamp of the most recent execution, or null
 */
public record JobInfoResponse(
        String jobName,
        String jobGroup,
        String jobType,
        String description,
        String triggerState,
        String cronExpression,
        String nextFireTime,
        String previousFireTime
) {}
