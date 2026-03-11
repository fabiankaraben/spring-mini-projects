package com.example.dynamicscheduling.dto;

import jakarta.validation.constraints.Min;

/**
 * Request DTO for updating the execution interval of an existing task.
 *
 * <p>Only the {@code intervalMs} field is accepted in this request.  Other
 * task properties (description, enabled state) are updated via dedicated
 * endpoints to keep operations focused and auditable.
 *
 * @param intervalMs new execution interval in milliseconds (minimum 1000)
 */
public record UpdateIntervalRequest(

    /**
     * New execution interval in milliseconds.
     * Minimum of 1000 ms (1 second) is enforced to prevent runaway scheduling.
     */
    @Min(value = 1000, message = "intervalMs must be at least 1000 (1 second)")
    long intervalMs

) {}
