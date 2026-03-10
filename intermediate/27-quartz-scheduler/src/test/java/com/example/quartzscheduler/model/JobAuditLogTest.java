package com.example.quartzscheduler.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link JobAuditLog} domain model.
 *
 * <h2>What is tested</h2>
 * <p>These are pure POJO tests – no Spring context, no database, no Quartz.
 * They verify:
 * <ul>
 *   <li>Constructor correctly populates mandatory fields.</li>
 *   <li>Optional fields are null until explicitly set.</li>
 *   <li>Setters update fields correctly.</li>
 *   <li>Duration calculation produces the expected value.</li>
 * </ul>
 *
 * <p>Even for simple domain objects, explicit tests serve as living
 * documentation of the entity's intended invariants and are valuable when
 * the model evolves (e.g. adding validation or computed properties).
 */
@DisplayName("JobAuditLog domain model unit tests")
class JobAuditLogTest {

    // ── Constructor ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructor initialises all mandatory fields correctly")
    void constructor_initsMandatoryFields() {
        // Given
        Instant now = Instant.now();

        // When: create an instance via the public constructor
        JobAuditLog log = new JobAuditLog(
                "myJob", "MAINTENANCE",
                "com.example.quartzscheduler.job.SampleLoggingJob",
                now, "SUCCESS");

        // Then: mandatory fields are populated
        assertThat(log.getJobName()).isEqualTo("myJob");
        assertThat(log.getJobGroup()).isEqualTo("MAINTENANCE");
        assertThat(log.getJobClass()).isEqualTo("com.example.quartzscheduler.job.SampleLoggingJob");
        assertThat(log.getFiredAt()).isEqualTo(now);
        assertThat(log.getStatus()).isEqualTo("SUCCESS");

        // And: optional fields default to null
        assertThat(log.getId()).isNull();         // set by JPA on persist
        assertThat(log.getFinishedAt()).isNull(); // not set yet
        assertThat(log.getDurationMs()).isNull(); // not set yet
        assertThat(log.getMessage()).isNull();    // not set yet
    }

    // ── Setters ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setFinishedAt and setDurationMs update the optional timing fields")
    void setters_updateTimingFields() {
        // Given
        Instant start  = Instant.parse("2025-06-01T10:00:00Z");
        Instant finish = Instant.parse("2025-06-01T10:00:00.250Z"); // 250 ms later

        JobAuditLog log = new JobAuditLog("job1", "GROUP1", "SomeJobClass", start, "SUCCESS");

        // When
        log.setFinishedAt(finish);
        log.setDurationMs(finish.toEpochMilli() - start.toEpochMilli());
        log.setMessage("Job completed in 250 ms.");

        // Then
        assertThat(log.getFinishedAt()).isEqualTo(finish);
        assertThat(log.getDurationMs()).isEqualTo(250L);
        assertThat(log.getMessage()).isEqualTo("Job completed in 250 ms.");
    }

    @Test
    @DisplayName("setStatus changes the status field")
    void setStatus_changesStatus() {
        // Given: a SUCCESS entry that later needs to be updated (hypothetical scenario)
        JobAuditLog log = new JobAuditLog("job2", "GROUP2", "SomeClass", Instant.now(), "SUCCESS");
        assertThat(log.getStatus()).isEqualTo("SUCCESS");

        // When
        log.setStatus("FAILED");

        // Then
        assertThat(log.getStatus()).isEqualTo("FAILED");
    }

    // ── Factory pattern helpers ───────────────────────────────────────────────────

    @Test
    @DisplayName("A FAILED entry can hold a long exception message")
    void failedEntry_canHoldLongMessage() {
        // Given
        String longMessage = "java.lang.RuntimeException: Simulated failure\n" +
                "  at com.example.SomeJob.execute(SomeJob.java:42)\n" +
                "  at org.quartz.core.JobRunShell.run(JobRunShell.java:202)";

        // When
        JobAuditLog log = new JobAuditLog("failJob", "TEST", "SomeClass", Instant.now(), "FAILED");
        log.setMessage(longMessage);

        // Then
        assertThat(log.getMessage()).isEqualTo(longMessage);
        assertThat(log.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Zero-duration execution is valid (very fast job)")
    void zeroDuration_isValid() {
        // Given: a job that completes in the same millisecond it started
        Instant ts = Instant.now();
        JobAuditLog log = new JobAuditLog("fastJob", "FAST", "FastClass", ts, "SUCCESS");
        log.setFinishedAt(ts);
        log.setDurationMs(0L);

        // Then
        assertThat(log.getDurationMs()).isZero();
    }
}
