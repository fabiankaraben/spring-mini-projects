package com.example.dynamicscheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new dynamic scheduled task.
 *
 * <p>Bean Validation constraints ensure that the REST controller rejects
 * malformed requests before they reach the service layer.
 *
 * @param taskName    unique logical identifier for the task (lowercase, hyphens allowed)
 * @param description optional human-readable description of what this task does
 * @param intervalMs  execution interval in milliseconds; minimum 1000 (1 second)
 * @param enabled     whether the task should start running immediately after creation
 */
public record CreateTaskRequest(

    /**
     * Unique logical name of the task.
     * Only lowercase letters, digits, and hyphens are allowed to keep names
     * URL-safe and easy to use as path variables in API calls.
     */
    @NotBlank(message = "taskName must not be blank")
    @Size(min = 1, max = 100, message = "taskName must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-z0-9-]+$",
             message = "taskName must contain only lowercase letters, digits, and hyphens")
    String taskName,

    /** Optional human-readable description, up to 500 characters. */
    @Size(max = 500, message = "description must not exceed 500 characters")
    String description,

    /**
     * Execution interval in milliseconds.
     * The minimum of 1000 ms prevents runaway high-frequency execution that
     * could saturate the thread pool or produce an unmanageable log volume.
     */
    @Min(value = 1000, message = "intervalMs must be at least 1000 (1 second)")
    long intervalMs,

    /** {@code true} to start the task immediately; {@code false} to create it in a paused state. */
    boolean enabled

) {}
