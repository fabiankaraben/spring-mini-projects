package com.example.distributedlock.web.dto;

import java.time.Instant;

import com.example.distributedlock.domain.TaskResult;
import com.example.distributedlock.domain.TaskStatus;

/**
 * HTTP response body for a task execution attempt.
 *
 * <p>This DTO is serialized to JSON by Jackson and returned to the HTTP caller.
 * It flattens the domain {@link TaskResult} record into a plain object that
 * is more JSON-friendly (e.g. {@link Instant} serializes to an ISO-8601 string).
 *
 * <p>Example JSON (COMPLETED):
 * <pre>
 * {
 *   "taskKey": "report-generation",
 *   "payload": "Q4 financial data",
 *   "status": "COMPLETED",
 *   "message": "Task completed successfully while holding the distributed lock.",
 *   "timestamp": "2025-01-01T12:00:00.123Z",
 *   "elapsedMs": 2053
 * }
 * </pre>
 *
 * <p>Example JSON (SKIPPED — lock held by another node):
 * <pre>
 * {
 *   "taskKey": "report-generation",
 *   "payload": "Q4 financial data",
 *   "status": "SKIPPED",
 *   "message": "Task skipped: could not acquire the distributed lock within the timeout...",
 *   "timestamp": "2025-01-01T12:00:00.500Z",
 *   "elapsedMs": 5001
 * }
 * </pre>
 */
public record TaskResponse(

        /** The task key that was submitted. */
        String taskKey,

        /** The payload that was (or was not) processed. */
        String payload,

        /** The outcome of the lock acquisition and task execution attempt. */
        TaskStatus status,

        /** Human-readable description of what happened. */
        String message,

        /** UTC timestamp of when the result was produced. */
        Instant timestamp,

        /** Total elapsed time in milliseconds from start to finish. */
        long elapsedMs
) {

    /**
     * Converts a domain {@link TaskResult} to this DTO.
     *
     * <p>The web layer is responsible for this conversion so that the domain
     * layer remains independent of HTTP / serialization concerns.
     *
     * @param result the domain result to convert
     * @return a new TaskResponse mirroring the result's fields
     */
    public static TaskResponse from(TaskResult result) {
        return new TaskResponse(
                result.taskKey(),
                result.payload(),
                result.status(),
                result.message(),
                result.timestamp(),
                result.elapsedMs()
        );
    }
}
