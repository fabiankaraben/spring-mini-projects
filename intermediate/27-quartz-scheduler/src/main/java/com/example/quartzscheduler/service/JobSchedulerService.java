package com.example.quartzscheduler.service;

import com.example.quartzscheduler.dto.JobInfoResponse;
import com.example.quartzscheduler.dto.ScheduleJobRequest;
import com.example.quartzscheduler.job.DataCleanupJob;
import com.example.quartzscheduler.job.ReportGenerationJob;
import com.example.quartzscheduler.job.SampleLoggingJob;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Service layer for all Quartz scheduling operations.
 *
 * <p>This class is the single point of contact between the REST controllers
 * and the Quartz {@link Scheduler}.  It encapsulates all job-management logic
 * so that controllers remain thin and focused on HTTP concerns.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Map a {@link ScheduleJobRequest} to the correct Quartz {@link Job} class.</li>
 *   <li>Build {@link JobDetail} and {@link CronTrigger} instances and register them.</li>
 *   <li>List, pause, resume, trigger-now, and delete jobs.</li>
 *   <li>Convert Quartz metadata to {@link JobInfoResponse} DTOs for the API.</li>
 * </ul>
 *
 * <h2>Quartz JDBC job-store</h2>
 * <p>Because the application uses {@code spring.quartz.job-store-type=jdbc},
 * all job definitions and trigger states are persisted to PostgreSQL.  This
 * means jobs survive application restarts and can be shared across multiple
 * application instances in a clustered deployment.
 */
@Service
public class JobSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerService.class);

    /** ISO-8601 formatter for converting {@link Date} → String in responses. */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    /**
     * The Quartz Scheduler auto-configured by Spring Boot.
     * Spring Boot's {@code QuartzAutoConfiguration} creates this bean when
     * the {@code spring-boot-starter-quartz} dependency is on the class-path.
     */
    private final Scheduler scheduler;

    public JobSchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    // ── Schedule ──────────────────────────────────────────────────────────────────

    /**
     * Schedules a new Quartz job based on the supplied request.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate that the cron expression is parseable by Quartz.</li>
     *   <li>Resolve the concrete {@link Job} class from the {@code jobType} discriminator.</li>
     *   <li>Build a durable, non-concurrent {@link JobDetail}.</li>
     *   <li>Build a {@link CronTrigger} linked to the job detail.</li>
     *   <li>Register both with the Quartz {@link Scheduler}.</li>
     * </ol>
     *
     * @param request validated request DTO from the REST controller
     * @return {@link JobInfoResponse} describing the newly scheduled job
     * @throws SchedulerException   if Quartz rejects the job or trigger
     * @throws IllegalArgumentException if the cron expression is invalid
     */
    public JobInfoResponse scheduleJob(ScheduleJobRequest request) throws SchedulerException {
        // Validate cron expression early to give a clear error message
        validateCronExpression(request.cronExpression());

        // Resolve the Job class from the user-supplied type discriminator
        Class<? extends Job> jobClass = resolveJobClass(request.jobType());

        // Build the JobKey – the unique identity of this job in the scheduler
        JobKey jobKey = new JobKey(request.jobName(), request.jobGroup());

        // Build the JobDetail – describes what to execute and stores the data map
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobKey)
                // storeDurably=true means the job is kept even if no triggers reference it
                .storeDurably(true)
                .withDescription(request.description())
                // For REPORTING jobs we need to initialise the run-count counter
                .usingJobData(ReportGenerationJob.KEY_RUN_COUNT, 0)
                .build();

        // Build the CronTrigger – defines when the job fires
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(request.jobName() + "_trigger", request.jobGroup())
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(request.cronExpression())
                        // Use UTC as the timezone so cron expressions are unambiguous
                        .inTimeZone(java.util.TimeZone.getTimeZone("UTC")))
                .build();

        // Register job and trigger; replaceExisting=true allows re-scheduling
        scheduler.scheduleJob(jobDetail, Set.of(trigger), true);

        log.info("Scheduled job={}/{} with cron={}", request.jobGroup(), request.jobName(),
                request.cronExpression());

        return buildJobInfo(jobDetail, trigger, scheduler.getTriggerState(trigger.getKey()));
    }

    // ── List all jobs ─────────────────────────────────────────────────────────────

    /**
     * Returns information about every job currently known to the scheduler.
     *
     * <p>Iterates over all job groups and job keys, loads the first trigger for
     * each job, and converts the metadata to {@link JobInfoResponse} DTOs.
     *
     * @return list of job info DTOs, one per registered job
     * @throws SchedulerException if Quartz fails to read the job store
     */
    public List<JobInfoResponse> listAllJobs() throws SchedulerException {
        List<JobInfoResponse> result = new ArrayList<>();

        // GroupMatcher.anyGroup() matches all groups – we want every job
        for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.anyGroup())) {
            JobDetail jobDetail = scheduler.getJobDetail(jobKey);
            if (jobDetail == null) continue;

            // A job may have multiple triggers; we take the first one for display
            List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
            if (triggers.isEmpty()) {
                // Job exists but has no trigger – include it with null trigger info
                result.add(new JobInfoResponse(
                        jobKey.getName(), jobKey.getGroup(),
                        resolveJobType(jobDetail.getJobClass()),
                        jobDetail.getDescription(),
                        "NO_TRIGGER", null, null, null));
            } else {
                Trigger trigger = triggers.get(0);
                Trigger.TriggerState state = scheduler.getTriggerState(trigger.getKey());
                result.add(buildJobInfo(jobDetail, trigger, state));
            }
        }
        return result;
    }

    // ── Pause ─────────────────────────────────────────────────────────────────────

    /**
     * Pauses all triggers for the named job.
     *
     * <p>While paused, the trigger will not fire even if its scheduled time
     * passes.  The job can be resumed later without losing its schedule.
     *
     * @param jobName  Quartz job name
     * @param jobGroup Quartz job group
     * @throws SchedulerException if the job does not exist or Quartz fails
     */
    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        assertJobExists(jobKey);
        scheduler.pauseJob(jobKey);
        log.info("Paused job={}/{}", jobGroup, jobName);
    }

    // ── Resume ────────────────────────────────────────────────────────────────────

    /**
     * Resumes all triggers for the named job, returning it to normal scheduling.
     *
     * @param jobName  Quartz job name
     * @param jobGroup Quartz job group
     * @throws SchedulerException if the job does not exist or Quartz fails
     */
    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        assertJobExists(jobKey);
        scheduler.resumeJob(jobKey);
        log.info("Resumed job={}/{}", jobGroup, jobName);
    }

    // ── Trigger now ───────────────────────────────────────────────────────────────

    /**
     * Immediately fires the named job outside of its normal schedule.
     *
     * <p>Quartz adds a one-shot trigger that fires at once.  The job's regular
     * cron schedule is not affected.
     *
     * @param jobName  Quartz job name
     * @param jobGroup Quartz job group
     * @throws SchedulerException if the job does not exist or Quartz fails
     */
    public void triggerJobNow(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        assertJobExists(jobKey);
        scheduler.triggerJob(jobKey);
        log.info("Triggered immediately job={}/{}", jobGroup, jobName);
    }

    // ── Delete ────────────────────────────────────────────────────────────────────

    /**
     * Deletes the named job and all its associated triggers from the scheduler.
     *
     * <p>If the job is currently executing, the current execution is allowed to
     * complete; only future firings are cancelled.
     *
     * @param jobName  Quartz job name
     * @param jobGroup Quartz job group
     * @throws SchedulerException if the job does not exist or Quartz fails
     */
    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        assertJobExists(jobKey);
        scheduler.deleteJob(jobKey);
        log.info("Deleted job={}/{}", jobGroup, jobName);
    }

    // ── Get single job ────────────────────────────────────────────────────────────

    /**
     * Returns detailed information about a single job.
     *
     * @param jobName  Quartz job name
     * @param jobGroup Quartz job group
     * @return JobInfoResponse DTO for the named job
     * @throws SchedulerException if the job does not exist or Quartz fails
     */
    public JobInfoResponse getJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = new JobKey(jobName, jobGroup);
        assertJobExists(jobKey);

        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

        if (triggers.isEmpty()) {
            return new JobInfoResponse(jobKey.getName(), jobKey.getGroup(),
                    resolveJobType(jobDetail.getJobClass()),
                    jobDetail.getDescription(),
                    "NO_TRIGGER", null, null, null);
        }

        Trigger trigger = triggers.get(0);
        return buildJobInfo(jobDetail, trigger, scheduler.getTriggerState(trigger.getKey()));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────────

    /**
     * Maps the user-supplied job-type string to the concrete Quartz {@link Job} class.
     *
     * @param jobType one of LOGGING, CLEANUP, REPORTING
     * @return the corresponding Job subclass
     * @throws IllegalArgumentException if the type is unknown
     */
    private Class<? extends Job> resolveJobClass(String jobType) {
        return switch (jobType.toUpperCase()) {
            case "LOGGING"   -> SampleLoggingJob.class;
            case "CLEANUP"   -> DataCleanupJob.class;
            case "REPORTING" -> ReportGenerationJob.class;
            default -> throw new IllegalArgumentException("Unknown job type: " + jobType);
        };
    }

    /**
     * Maps a {@link Job} implementation class back to the friendly type string
     * used in API responses.
     *
     * @param jobClass the concrete job class
     * @return friendly type label, or "UNKNOWN" if unrecognised
     */
    private String resolveJobType(Class<? extends Job> jobClass) {
        if (jobClass == SampleLoggingJob.class)   return "LOGGING";
        if (jobClass == DataCleanupJob.class)      return "CLEANUP";
        if (jobClass == ReportGenerationJob.class) return "REPORTING";
        return "UNKNOWN";
    }

    /**
     * Validates a Quartz cron expression by attempting to parse it.
     * Throws {@link IllegalArgumentException} with a clear message on failure.
     *
     * @param cronExpression Quartz cron expression to validate
     */
    private void validateCronExpression(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException(
                    "Invalid Quartz cron expression: '" + cronExpression + "'. " +
                    "Quartz cron has 6 fields: seconds minutes hours day-of-month month day-of-week. " +
                    "Example: '0 0/5 * * * ?' fires every 5 minutes.");
        }
    }

    /**
     * Asserts that a job with the given key exists in the scheduler.
     * Throws {@link IllegalArgumentException} (mapped to 404 by the exception handler) if not.
     *
     * @param jobKey the key to check
     * @throws SchedulerException if Quartz fails to look up the job
     */
    private void assertJobExists(JobKey jobKey) throws SchedulerException {
        if (!scheduler.checkExists(jobKey)) {
            throw new IllegalArgumentException(
                    "Job not found: " + jobKey.getGroup() + "/" + jobKey.getName());
        }
    }

    /**
     * Converts Quartz job/trigger metadata to a {@link JobInfoResponse} DTO.
     *
     * @param jobDetail   the Quartz job detail
     * @param trigger     the primary trigger associated with the job
     * @param state       the current trigger state
     * @return populated response DTO
     */
    private JobInfoResponse buildJobInfo(JobDetail jobDetail, Trigger trigger,
                                          Trigger.TriggerState state) {
        // Extract cron expression if the trigger is a CronTrigger
        String cronExpression = (trigger instanceof CronTrigger ct)
                ? ct.getCronExpression() : null;

        // Convert java.util.Date to ISO-8601 String (nullable)
        String nextFireTime     = formatDate(trigger.getNextFireTime());
        String previousFireTime = formatDate(trigger.getPreviousFireTime());

        return new JobInfoResponse(
                jobDetail.getKey().getName(),
                jobDetail.getKey().getGroup(),
                resolveJobType(jobDetail.getJobClass()),
                jobDetail.getDescription(),
                state.name(),
                cronExpression,
                nextFireTime,
                previousFireTime
        );
    }

    /**
     * Formats a {@link Date} as an ISO-8601 UTC string, or returns {@code null}
     * if the date is {@code null}.
     *
     * @param date the date to format
     * @return ISO-8601 string or null
     */
    private String formatDate(Date date) {
        if (date == null) return null;
        return FORMATTER.format(ZonedDateTime.ofInstant(date.toInstant(), ZoneId.of("UTC")));
    }
}
