package com.example.quartzscheduler.service;

import com.example.quartzscheduler.dto.JobInfoResponse;
import com.example.quartzscheduler.dto.ScheduleJobRequest;
import com.example.quartzscheduler.job.DataCleanupJob;
import com.example.quartzscheduler.job.ReportGenerationJob;
import com.example.quartzscheduler.job.SampleLoggingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JobSchedulerService}.
 *
 * <h2>Testing approach</h2>
 * <p>These tests verify the service's behaviour in isolation by mocking the
 * Quartz {@link Scheduler}.  No Docker container, no Spring context, and no
 * database are required – making this test suite fast and suitable for
 * developer inner-loop feedback.
 *
 * <h2>What is covered</h2>
 * <ul>
 *   <li>Correct {@link Job} class is resolved for each job-type discriminator.</li>
 *   <li>{@link JobDetail} and {@link CronTrigger} are registered with the scheduler.</li>
 *   <li>Invalid cron expressions are rejected with a clear error message.</li>
 *   <li>Pause / resume / trigger-now / delete delegate to the Quartz Scheduler.</li>
 *   <li>"Job not found" throws {@link IllegalArgumentException} with the right message.</li>
 *   <li>List-jobs returns a DTO for every job key reported by the scheduler.</li>
 * </ul>
 *
 * <h2>Key annotations</h2>
 * <ul>
 *   <li>{@link ExtendWith} with {@link MockitoExtension} bootstraps Mockito
 *       without a Spring context.</li>
 *   <li>{@link Mock} creates a mock {@link Scheduler} injected into the service
 *       under test via constructor injection.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobSchedulerService unit tests")
class JobSchedulerServiceTest {

    /** Mocked Quartz Scheduler – no real database or Docker required. */
    @Mock
    private Scheduler scheduler;

    /** The system under test – receives the mocked scheduler via constructor. */
    private JobSchedulerService service;

    @BeforeEach
    void setUp() {
        // Construct the service with the mock scheduler injected directly
        service = new JobSchedulerService(scheduler);
    }

    // ── scheduleJob ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("scheduleJob with LOGGING type registers a SampleLoggingJob")
    void scheduleJob_loggingType_registersLoggingJob() throws SchedulerException {
        // Given: a valid schedule request for a LOGGING job
        ScheduleJobRequest request = new ScheduleJobRequest(
                "myLoggingJob", "MAINTENANCE", "LOGGING",
                "0 0/5 * * * ?",  // every 5 minutes
                "Test logging job");

        // Stub the scheduler to return a mock trigger key for getTriggerState
        TriggerKey triggerKey = TriggerKey.triggerKey("myLoggingJob_trigger", "MAINTENANCE");
        when(scheduler.getTriggerState(any())).thenReturn(Trigger.TriggerState.NORMAL);
        // scheduleJob(JobDetail, Set, boolean) returns void – no stub needed; Mockito no-ops by default

        // When: scheduling the job
        JobInfoResponse response = service.scheduleJob(request);

        // Then: scheduleJob was called on the Quartz scheduler
        ArgumentCaptor<JobDetail> jobDetailCaptor = ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler).scheduleJob(jobDetailCaptor.capture(), any(Set.class), anyBoolean());

        // Assert the correct job class was used
        assertThat(jobDetailCaptor.getValue().getJobClass())
                .isEqualTo(SampleLoggingJob.class);

        // Assert response DTO reflects the request
        assertThat(response.jobName()).isEqualTo("myLoggingJob");
        assertThat(response.jobGroup()).isEqualTo("MAINTENANCE");
        assertThat(response.jobType()).isEqualTo("LOGGING");
        assertThat(response.triggerState()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("scheduleJob with CLEANUP type registers a DataCleanupJob")
    void scheduleJob_cleanupType_registersCleanupJob() throws SchedulerException {
        // Given: a valid schedule request for a CLEANUP job
        ScheduleJobRequest request = new ScheduleJobRequest(
                "cleanupJob", "MAINTENANCE", "CLEANUP",
                "0 0 3 * * ?",  // daily at 03:00 UTC
                "Daily cleanup");

        when(scheduler.getTriggerState(any())).thenReturn(Trigger.TriggerState.NORMAL);
        // scheduleJob(JobDetail, Set, boolean) returns void – no stub needed

        // When
        service.scheduleJob(request);

        // Then: the DataCleanupJob class was used
        ArgumentCaptor<JobDetail> captor = ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler).scheduleJob(captor.capture(), any(Set.class), anyBoolean());
        assertThat(captor.getValue().getJobClass()).isEqualTo(DataCleanupJob.class);
    }

    @Test
    @DisplayName("scheduleJob with REPORTING type registers a ReportGenerationJob")
    void scheduleJob_reportingType_registersReportingJob() throws SchedulerException {
        // Given: a valid schedule request for a REPORTING job
        ScheduleJobRequest request = new ScheduleJobRequest(
                "reportJob", "REPORTING", "REPORTING",
                "0 0 9 ? * MON-FRI",  // weekdays at 09:00 UTC
                "Weekly report");

        when(scheduler.getTriggerState(any())).thenReturn(Trigger.TriggerState.NORMAL);
        // scheduleJob(JobDetail, Set, boolean) returns void – no stub needed

        // When
        service.scheduleJob(request);

        // Then: the ReportGenerationJob class was used
        ArgumentCaptor<JobDetail> captor = ArgumentCaptor.forClass(JobDetail.class);
        verify(scheduler).scheduleJob(captor.capture(), any(Set.class), anyBoolean());
        assertThat(captor.getValue().getJobClass()).isEqualTo(ReportGenerationJob.class);
    }

    @Test
    @DisplayName("scheduleJob with invalid cron expression throws IllegalArgumentException")
    void scheduleJob_invalidCron_throwsIllegalArgumentException() {
        // Given: a request with an unparseable cron expression
        ScheduleJobRequest request = new ScheduleJobRequest(
                "badCronJob", "TEST", "LOGGING",
                "not-a-cron-expression",
                "Should fail");

        // When / Then: IllegalArgumentException with a clear message
        assertThatThrownBy(() -> service.scheduleJob(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Quartz cron expression");
    }

    // ── pauseJob ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pauseJob delegates to scheduler.pauseJob when job exists")
    void pauseJob_existingJob_delegatesToScheduler() throws SchedulerException {
        // Given: the job exists in the scheduler
        JobKey jobKey = new JobKey("loggingJob", "MAINTENANCE");
        when(scheduler.checkExists(jobKey)).thenReturn(true);

        // When
        service.pauseJob("loggingJob", "MAINTENANCE");

        // Then: scheduler.pauseJob was called with the correct key
        verify(scheduler).pauseJob(jobKey);
    }

    @Test
    @DisplayName("pauseJob throws IllegalArgumentException when job does not exist")
    void pauseJob_nonExistentJob_throwsIllegalArgumentException() throws SchedulerException {
        // Given: the job does NOT exist
        when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> service.pauseJob("ghost", "MISSING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Job not found");
    }

    // ── resumeJob ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("resumeJob delegates to scheduler.resumeJob when job exists")
    void resumeJob_existingJob_delegatesToScheduler() throws SchedulerException {
        // Given: the job exists
        JobKey jobKey = new JobKey("loggingJob", "MAINTENANCE");
        when(scheduler.checkExists(jobKey)).thenReturn(true);

        // When
        service.resumeJob("loggingJob", "MAINTENANCE");

        // Then
        verify(scheduler).resumeJob(jobKey);
    }

    // ── triggerJobNow ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("triggerJobNow delegates to scheduler.triggerJob when job exists")
    void triggerJobNow_existingJob_delegatesToScheduler() throws SchedulerException {
        // Given: the job exists
        JobKey jobKey = new JobKey("loggingJob", "MAINTENANCE");
        when(scheduler.checkExists(jobKey)).thenReturn(true);

        // When
        service.triggerJobNow("loggingJob", "MAINTENANCE");

        // Then: scheduler.triggerJob was called
        verify(scheduler).triggerJob(jobKey);
    }

    // ── deleteJob ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteJob delegates to scheduler.deleteJob when job exists")
    void deleteJob_existingJob_delegatesToScheduler() throws SchedulerException {
        // Given: the job exists
        JobKey jobKey = new JobKey("loggingJob", "MAINTENANCE");
        when(scheduler.checkExists(jobKey)).thenReturn(true);

        // When
        service.deleteJob("loggingJob", "MAINTENANCE");

        // Then
        verify(scheduler).deleteJob(jobKey);
    }

    // ── listAllJobs ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listAllJobs returns empty list when no jobs are scheduled")
    void listAllJobs_noJobs_returnsEmptyList() throws SchedulerException {
        // Given: the scheduler reports no job keys in any group
        when(scheduler.getJobKeys(GroupMatcher.anyGroup()))
                .thenReturn(Collections.emptySet());

        // When
        List<JobInfoResponse> result = service.listAllJobs();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("listAllJobs returns a DTO for each registered job")
    void listAllJobs_withJobs_returnsDtoPerJob() throws SchedulerException {
        // Given: the scheduler knows about one job
        JobKey jobKey = new JobKey("loggingJob", "MAINTENANCE");
        when(scheduler.getJobKeys(GroupMatcher.anyGroup()))
                .thenReturn(Set.of(jobKey));

        // Build a stubbed JobDetail using the real Quartz builder
        JobDetail jobDetail = JobBuilder.newJob(SampleLoggingJob.class)
                .withIdentity(jobKey)
                .withDescription("A logging job")
                .storeDurably(true)
                .build();
        when(scheduler.getJobDetail(jobKey)).thenReturn(jobDetail);

        // Build a CronTrigger stub
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("loggingJob_trigger", "MAINTENANCE")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/5 * * * ?"))
                .build();
        // doReturn bypasses Mockito's type-inference limitation with List<? extends Trigger>
        doReturn(List.of(trigger)).when(scheduler).getTriggersOfJob(jobKey);
        when(scheduler.getTriggerState(trigger.getKey()))
                .thenReturn(Trigger.TriggerState.NORMAL);

        // When
        List<JobInfoResponse> result = service.listAllJobs();

        // Then: one DTO returned with correct fields
        assertThat(result).hasSize(1);
        JobInfoResponse dto = result.get(0);
        assertThat(dto.jobName()).isEqualTo("loggingJob");
        assertThat(dto.jobGroup()).isEqualTo("MAINTENANCE");
        assertThat(dto.jobType()).isEqualTo("LOGGING");
        assertThat(dto.triggerState()).isEqualTo("NORMAL");
        assertThat(dto.cronExpression()).isEqualTo("0 0/5 * * * ?");
    }
}
