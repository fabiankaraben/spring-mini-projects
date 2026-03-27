package com.example.distributedlock.domain;

/**
 * Represents the possible states of a task execution.
 *
 * <p>The status reflects whether the distributed lock was successfully acquired
 * and whether the task completed its work without interruption.
 *
 * <ul>
 *   <li>{@link #COMPLETED}  — the lock was acquired and the task ran to completion.</li>
 *   <li>{@link #SKIPPED}    — the lock was NOT acquired within the timeout window;
 *                             another node/thread is currently executing the same task.</li>
 *   <li>{@link #INTERRUPTED} — the thread was interrupted while waiting for the lock
 *                             or during task processing.</li>
 * </ul>
 */
public enum TaskStatus {

    /**
     * The distributed lock was acquired successfully and the task ran to completion.
     * This is the happy-path result.
     */
    COMPLETED,

    /**
     * The distributed lock could not be acquired within the configured timeout.
     * Another process is currently executing a task with the same key.
     * The caller should retry later or accept that the task is already running.
     */
    SKIPPED,

    /**
     * The current thread was interrupted while waiting for the lock or during
     * task processing. This is an abnormal termination — the task did NOT run.
     */
    INTERRUPTED
}
