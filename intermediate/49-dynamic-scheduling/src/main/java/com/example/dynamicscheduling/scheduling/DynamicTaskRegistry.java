package com.example.dynamicscheduling.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * In-memory registry that tracks every dynamically scheduled task at runtime.
 *
 * <p>This class is the central coordination point between:
 * <ul>
 *   <li>The REST API – which reads and writes schedule parameters.</li>
 *   <li>The {@link DynamicSchedulingConfigurer} – which registers tasks with
 *       Spring's {@link org.springframework.scheduling.TaskScheduler}.</li>
 *   <li>Each running {@link DynamicTask} – which consults its own entry here on
 *       every execution to decide whether it is still enabled and what the current
 *       interval should be.</li>
 * </ul>
 *
 * <h2>How dynamic rescheduling works</h2>
 * <p>Spring's {@link org.springframework.scheduling.config.ScheduledTaskRegistrar}
 * does not natively support mutating a trigger after the task has been registered.
 * To work around this, each task is registered with a custom {@link org.springframework.scheduling.Trigger}
 * that reads the interval from this registry on every invocation of
 * {@link org.springframework.scheduling.Trigger#nextExecution(org.springframework.scheduling.TriggerContext)}.
 * Therefore, the very next time the trigger is evaluated after a REST update, the
 * new interval is already in effect – no restart required.
 *
 * <h2>Thread safety</h2>
 * <p>All state is stored in {@link ConcurrentHashMap} instances to allow safe
 * concurrent access from the scheduler thread pool and the web request threads.
 */
@Component
public class DynamicTaskRegistry {

    private static final Logger log = LoggerFactory.getLogger(DynamicTaskRegistry.class);

    /**
     * Current interval (milliseconds) per task name.
     * The scheduler's trigger implementation reads from this map on every
     * {@code nextExecutionTime} evaluation.
     */
    private final Map<String, Long> intervals = new ConcurrentHashMap<>();

    /**
     * Enabled/disabled flag per task name.
     * A disabled task has its {@link ScheduledFuture} cancelled and will not
     * produce new execution log records until re-enabled.
     */
    private final Map<String, Boolean> enabledFlags = new ConcurrentHashMap<>();

    /**
     * Holds the live {@link ScheduledFuture} for each running task.
     * Used to cancel a task when it is disabled or deleted.
     */
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    // ── Interval management ───────────────────────────────────────────────────────

    /**
     * Registers (or updates) the interval for a named task.
     *
     * <p>If the task is already running, its trigger will pick up the new
     * interval on the next evaluation cycle without any explicit restart.
     *
     * @param taskName   unique logical name of the task
     * @param intervalMs execution interval in milliseconds (must be ≥ 1000)
     */
    public void setInterval(String taskName, long intervalMs) {
        intervals.put(taskName, intervalMs);
        log.debug("Registry: updated interval for task='{}' to {} ms", taskName, intervalMs);
    }

    /**
     * Returns the current interval for the named task.
     *
     * @param taskName   unique logical name of the task
     * @param defaultMs  fallback value if no interval has been registered
     * @return current interval in milliseconds
     */
    public long getInterval(String taskName, long defaultMs) {
        return intervals.getOrDefault(taskName, defaultMs);
    }

    // ── Enabled flag management ───────────────────────────────────────────────────

    /**
     * Sets the enabled/disabled state for a named task.
     *
     * @param taskName unique logical name of the task
     * @param enabled  {@code true} to enable, {@code false} to disable
     */
    public void setEnabled(String taskName, boolean enabled) {
        enabledFlags.put(taskName, enabled);
        log.debug("Registry: set enabled={} for task='{}'", enabled, taskName);
    }

    /**
     * Returns whether the named task is currently enabled.
     *
     * @param taskName       unique logical name of the task
     * @param defaultEnabled fallback value if no flag has been registered
     * @return {@code true} if the task should execute
     */
    public boolean isEnabled(String taskName, boolean defaultEnabled) {
        return enabledFlags.getOrDefault(taskName, defaultEnabled);
    }

    // ── Future management ─────────────────────────────────────────────────────────

    /**
     * Stores the {@link ScheduledFuture} returned when the task was submitted
     * to the underlying thread pool.  Required to cancel or reschedule the task.
     *
     * @param taskName unique logical name of the task
     * @param future   the scheduled future to store
     */
    public void registerFuture(String taskName, ScheduledFuture<?> future) {
        futures.put(taskName, future);
    }

    /**
     * Cancels the scheduled future for the named task (if any).
     * Passing {@code mayInterruptIfRunning=false} lets an in-progress execution
     * finish before the task is stopped.
     *
     * @param taskName unique logical name of the task
     */
    public void cancelFuture(String taskName) {
        ScheduledFuture<?> future = futures.remove(taskName);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            log.debug("Registry: cancelled scheduled future for task='{}'", taskName);
        }
    }

    // ── Query helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all registered task names.
     *
     * @return set of task names present in the interval map
     */
    public Set<String> getRegisteredTaskNames() {
        return Collections.unmodifiableSet(intervals.keySet());
    }

    /**
     * Returns {@code true} if a task with the given name has been registered
     * in this registry.
     *
     * @param taskName unique logical name of the task
     * @return {@code true} if the task exists in the registry
     */
    public boolean contains(String taskName) {
        return intervals.containsKey(taskName);
    }

    /**
     * Removes all registry state for the named task.
     * Called when a task is deleted via the REST API.
     *
     * @param taskName unique logical name of the task
     */
    public void remove(String taskName) {
        cancelFuture(taskName);
        intervals.remove(taskName);
        enabledFlags.remove(taskName);
        log.debug("Registry: removed task='{}'", taskName);
    }
}
