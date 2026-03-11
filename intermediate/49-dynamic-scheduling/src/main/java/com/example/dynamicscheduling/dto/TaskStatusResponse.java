package com.example.dynamicscheduling.dto;

/**
 * Response DTO that describes the current state of a dynamic scheduled task.
 *
 * <p>Returned by list, get, create, update, enable, and disable endpoints.
 * It combines the persisted database configuration with the live in-memory
 * registry value so callers can see both what is stored and what is currently
 * active in the scheduler.
 *
 * @param taskName          unique logical name of the task
 * @param description       optional human-readable description
 * @param configuredIntervalMs interval stored in the database (ms)
 * @param liveIntervalMs    interval currently active in the in-memory registry (ms);
 *                          differs from {@code configuredIntervalMs} only briefly
 *                          during the propagation window after an update
 * @param enabled           whether the task is currently active in the scheduler
 */
public record TaskStatusResponse(
    String  taskName,
    String  description,
    long    configuredIntervalMs,
    long    liveIntervalMs,
    boolean enabled
) {}
