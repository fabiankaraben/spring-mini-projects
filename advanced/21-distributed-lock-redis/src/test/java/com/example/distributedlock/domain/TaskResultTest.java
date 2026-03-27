package com.example.distributedlock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link TaskResult} value object.
 *
 * <p>These are pure unit tests — no Spring context, no Redis, no Docker.
 * They verify the factory methods create correct immutable domain objects.
 *
 * <p>Using AssertJ for fluent, readable assertions.
 */
@DisplayName("TaskResult — unit tests")
class TaskResultTest {

    // -------------------------------------------------------------------------
    // Factory method: completed()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("completed() sets all fields correctly")
    void completed_setsAllFieldsCorrectly() {
        // given
        String taskKey  = "report-job";
        String payload  = "Q4 data";
        long   elapsed  = 1500L;

        // when
        TaskResult result = TaskResult.completed(taskKey, payload, elapsed);

        // then — all fields must match the expected values
        assertThat(result.taskKey()).isEqualTo(taskKey);
        assertThat(result.payload()).isEqualTo(payload);
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.elapsedMs()).isEqualTo(elapsed);
        assertThat(result.message()).isNotBlank();
        // timestamp must be very recent (within a few seconds of now)
        assertThat(result.timestamp())
                .isAfter(Instant.now().minusSeconds(5))
                .isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    @DisplayName("completed() status is COMPLETED")
    void completed_statusIsCompleted() {
        TaskResult result = TaskResult.completed("k", "p", 100L);
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    // -------------------------------------------------------------------------
    // Factory method: skipped()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("skipped() sets status to SKIPPED and preserves fields")
    void skipped_setsStatusAndFields() {
        // given
        String taskKey = "email-sender";
        String payload = "user@example.com";
        long   elapsed = 5001L;

        // when
        TaskResult result = TaskResult.skipped(taskKey, payload, elapsed);

        // then
        assertThat(result.taskKey()).isEqualTo(taskKey);
        assertThat(result.payload()).isEqualTo(payload);
        assertThat(result.status()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(result.elapsedMs()).isEqualTo(elapsed);
        assertThat(result.message()).contains("skipped");
        assertThat(result.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("skipped() message mentions lock acquisition failure")
    void skipped_messageMentionsLockFailure() {
        // The message should be informative enough for callers to understand why the task was skipped
        TaskResult result = TaskResult.skipped("k", "p", 0L);
        assertThat(result.message())
                .containsIgnoringCase("lock")
                .containsIgnoringCase("timeout");
    }

    // -------------------------------------------------------------------------
    // Factory method: interrupted()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("interrupted() sets status to INTERRUPTED and preserves fields")
    void interrupted_setsStatusAndFields() {
        // given
        String taskKey = "batch-job";
        String payload = "record-set-42";
        long   elapsed = 200L;

        // when
        TaskResult result = TaskResult.interrupted(taskKey, payload, elapsed);

        // then
        assertThat(result.taskKey()).isEqualTo(taskKey);
        assertThat(result.payload()).isEqualTo(payload);
        assertThat(result.status()).isEqualTo(TaskStatus.INTERRUPTED);
        assertThat(result.elapsedMs()).isEqualTo(elapsed);
        assertThat(result.message()).contains("interrupted");
        assertThat(result.timestamp()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Record immutability — verify that records are pure value objects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Two TaskResults with identical fields are equal (record equality)")
    void recordEquality_identicalFields() {
        // Records generate equals() based on all components.
        // We can't test this across factory methods because timestamp() is Instant.now()
        // which differs between calls. Instead build directly.
        Instant ts = Instant.parse("2025-01-01T12:00:00Z");

        TaskResult r1 = new TaskResult("k", "p", TaskStatus.COMPLETED, "msg", ts, 100L);
        TaskResult r2 = new TaskResult("k", "p", TaskStatus.COMPLETED, "msg", ts, 100L);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    @DisplayName("Two TaskResults with different taskKeys are not equal")
    void recordEquality_differentTaskKeys() {
        Instant ts = Instant.parse("2025-01-01T12:00:00Z");

        TaskResult r1 = new TaskResult("key-A", "p", TaskStatus.COMPLETED, "msg", ts, 100L);
        TaskResult r2 = new TaskResult("key-B", "p", TaskStatus.COMPLETED, "msg", ts, 100L);

        assertThat(r1).isNotEqualTo(r2);
    }
}
