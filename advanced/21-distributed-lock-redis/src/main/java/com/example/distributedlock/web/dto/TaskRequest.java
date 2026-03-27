package com.example.distributedlock.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * HTTP request body for submitting a task.
 *
 * <p>This DTO is deserialized from JSON by Jackson and validated by Bean Validation
 * before it reaches the controller method. Invalid payloads are rejected with a
 * 400 Bad Request response automatically by Spring's {@code @Valid} handling.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "taskKey": "report-generation",
 *   "payload": "Q4 financial data"
 * }
 * </pre>
 */
public record TaskRequest(

        /**
         * Logical identifier for the task.
         *
         * <p>This value is used as the suffix of the Redis lock key
         * (e.g. {@code "task-lock:report-generation"}), so two requests with
         * the same {@code taskKey} will contend for the same distributed lock.
         *
         * <p>Two requests with <em>different</em> taskKeys acquire independent
         * locks and can run fully concurrently — the lock only serialises
         * tasks that share the same key.
         */
        @NotBlank(message = "taskKey must not be blank")
        @Size(max = 128, message = "taskKey must be at most 128 characters")
        String taskKey,

        /**
         * Arbitrary data to be "processed" by the task.
         *
         * <p>In this demo the payload is only logged to show that the correct
         * data is handled inside the lock. In a real application this could be
         * a job descriptor, a record ID, configuration, etc.
         */
        @NotBlank(message = "payload must not be blank")
        @Size(max = 1024, message = "payload must be at most 1024 characters")
        String payload
) {}
