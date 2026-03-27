package com.example.distributedlock.domain;

import java.time.Instant;

/**
 * Immutable value object representing the outcome of a single task execution attempt.
 *
 * <p>This is the domain object returned by {@link TaskService} after it attempts to
 * acquire the distributed lock and execute the task. It captures:
 * <ul>
 *   <li>The task key (lock name) used to identify the task in Redis.</li>
 *   <li>The payload that was (or was not) processed.</li>
 *   <li>The final {@link TaskStatus}: COMPLETED, SKIPPED, or INTERRUPTED.</li>
 *   <li>A human-readable message describing what happened.</li>
 *   <li>The wall-clock timestamp of when this result was produced.</li>
 *   <li>How long the task execution took in milliseconds.</li>
 * </ul>
 *
 * <p>Using an immutable record makes TaskResult thread-safe and easy to reason about:
 * once created, none of its fields can be accidentally mutated by concurrent code.
 */
public record TaskResult(

        /**
         * The key that identifies this task's distributed lock in Redis.
         * Two submissions with the same taskKey contend for the same lock.
         */
        String taskKey,

        /**
         * The payload that was submitted with the task.
         */
        String payload,

        /**
         * The final status of the task execution attempt.
         *
         * @see TaskStatus
         */
        TaskStatus status,

        /**
         * A human-readable description of the outcome, suitable for API responses
         * and log messages.
         */
        String message,

        /**
         * The UTC timestamp at which this result was created.
         */
        Instant timestamp,

        /**
         * Total elapsed time in milliseconds from lock-acquisition attempt to
         * lock release (or timeout / interruption).
         */
        long elapsedMs
) {

    /**
     * Factory method: creates a COMPLETED result.
     *
     * @param taskKey   the Redis lock key
     * @param payload   the processed payload
     * @param elapsedMs total duration in milliseconds
     * @return a new TaskResult with status COMPLETED
     */
    public static TaskResult completed(String taskKey, String payload, long elapsedMs) {
        return new TaskResult(
                taskKey,
                payload,
                TaskStatus.COMPLETED,
                "Task completed successfully while holding the distributed lock.",
                Instant.now(),
                elapsedMs
        );
    }

    /**
     * Factory method: creates a SKIPPED result.
     *
     * <p>Used when the distributed lock could not be acquired within the configured
     * timeout because another node/thread is currently executing the same task.
     *
     * @param taskKey   the Redis lock key
     * @param payload   the payload that was NOT processed
     * @param elapsedMs time spent waiting before giving up
     * @return a new TaskResult with status SKIPPED
     */
    public static TaskResult skipped(String taskKey, String payload, long elapsedMs) {
        return new TaskResult(
                taskKey,
                payload,
                TaskStatus.SKIPPED,
                "Task skipped: could not acquire the distributed lock within the timeout. "
                        + "Another instance is already executing this task.",
                Instant.now(),
                elapsedMs
        );
    }

    /**
     * Factory method: creates an INTERRUPTED result.
     *
     * <p>Used when the thread is interrupted while waiting for the lock or while
     * holding it and processing the task. The interrupt flag is preserved by
     * re-setting it before returning so callers can detect it.
     *
     * @param taskKey   the Redis lock key
     * @param payload   the payload that was NOT fully processed
     * @param elapsedMs time elapsed before interruption
     * @return a new TaskResult with status INTERRUPTED
     */
    public static TaskResult interrupted(String taskKey, String payload, long elapsedMs) {
        return new TaskResult(
                taskKey,
                payload,
                TaskStatus.INTERRUPTED,
                "Task interrupted: the thread was interrupted while waiting for or holding the lock.",
                Instant.now(),
                elapsedMs
        );
    }
}
