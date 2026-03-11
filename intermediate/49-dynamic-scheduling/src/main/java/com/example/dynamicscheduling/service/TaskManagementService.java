package com.example.dynamicscheduling.service;

import com.example.dynamicscheduling.dto.CreateTaskRequest;
import com.example.dynamicscheduling.dto.TaskStatusResponse;
import com.example.dynamicscheduling.dto.UpdateIntervalRequest;
import com.example.dynamicscheduling.model.TaskConfig;
import com.example.dynamicscheduling.repository.TaskConfigRepository;
import com.example.dynamicscheduling.scheduling.DynamicSchedulingConfigurer;
import com.example.dynamicscheduling.scheduling.DynamicTaskRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for all task management operations exposed through the REST API.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Create new dynamic tasks and register them with the scheduler at runtime.</li>
 *   <li>Update the interval of an existing task – reflected immediately without restart.</li>
 *   <li>Enable or disable tasks – pauses/resumes the underlying scheduled future.</li>
 *   <li>Delete tasks – cancels the future and removes the database record.</li>
 *   <li>Query task status – combines database config with live registry values.</li>
 * </ul>
 *
 * <h2>Dynamic update mechanism</h2>
 * <p>When an interval is updated:
 * <ol>
 *   <li>The new value is written to PostgreSQL via JPA ({@link TaskConfigRepository}).</li>
 *   <li>The in-memory registry ({@link DynamicTaskRegistry}) is updated immediately.</li>
 *   <li>On the next trigger evaluation cycle, the scheduler reads the new value
 *       from the registry and applies it – no task cancellation or restart needed.</li>
 * </ol>
 *
 * <p>When a task is disabled, its {@link java.util.concurrent.ScheduledFuture}
 * is cancelled via the registry.  When re-enabled, a brand-new future is scheduled.
 */
@Service
public class TaskManagementService {

    private static final Logger log = LoggerFactory.getLogger(TaskManagementService.class);

    /** Persistent store of task configurations. */
    private final TaskConfigRepository taskConfigRepository;

    /** Live in-memory registry of intervals and enabled flags. */
    private final DynamicTaskRegistry registry;

    /**
     * Configurer used to register new tasks with the underlying
     * {@link org.springframework.scheduling.TaskScheduler} at runtime.
     */
    private final DynamicSchedulingConfigurer configurer;

    public TaskManagementService(TaskConfigRepository taskConfigRepository,
                                 DynamicTaskRegistry registry,
                                 DynamicSchedulingConfigurer configurer) {
        this.taskConfigRepository = taskConfigRepository;
        this.registry             = registry;
        this.configurer           = configurer;
    }

    // ── Create ────────────────────────────────────────────────────────────────────

    /**
     * Creates a new task, persists its configuration to PostgreSQL, and
     * immediately registers it with the scheduler if it is enabled.
     *
     * @param request validated request DTO containing the task details
     * @return {@link TaskStatusResponse} representing the newly created task
     * @throws IllegalArgumentException if a task with the same name already exists
     */
    @Transactional
    public TaskStatusResponse createTask(CreateTaskRequest request) {
        String taskName = request.taskName();

        // Guard: task names must be globally unique
        if (taskConfigRepository.existsByTaskName(taskName)) {
            throw new IllegalArgumentException(
                "A task with name '" + taskName + "' already exists.");
        }

        // Persist the task configuration to the database
        TaskConfig config = new TaskConfig(
            taskName,
            request.description(),
            request.intervalMs(),
            request.enabled()
        );
        config = taskConfigRepository.save(config);
        log.info("Created task='{}' intervalMs={} enabled={}", taskName,
                 request.intervalMs(), request.enabled());

        // Register the task in the scheduler (populates the registry and schedules the future)
        configurer.registerTask(config);

        return toResponse(config);
    }

    // ── Update interval ───────────────────────────────────────────────────────────

    /**
     * Updates the execution interval of an existing task.
     *
     * <p>The new interval is written to PostgreSQL and then immediately
     * applied to the in-memory registry.  The scheduler will use the new
     * interval on the very next trigger evaluation – no restart required.
     *
     * @param taskName unique logical name of the task to update
     * @param request  request DTO containing the new interval
     * @return updated {@link TaskStatusResponse}
     * @throws IllegalArgumentException if the task does not exist
     */
    @Transactional
    public TaskStatusResponse updateInterval(String taskName, UpdateIntervalRequest request) {
        TaskConfig config = findOrThrow(taskName);

        long oldInterval = config.getIntervalMs();
        config.setIntervalMs(request.intervalMs());
        config = taskConfigRepository.save(config);

        // Immediately update the live registry so the trigger picks up the new value
        registry.setInterval(taskName, request.intervalMs());

        log.info("Updated interval for task='{}': {} ms -> {} ms",
                 taskName, oldInterval, request.intervalMs());

        return toResponse(config);
    }

    // ── Enable / Disable ──────────────────────────────────────────────────────────

    /**
     * Enables a previously disabled task, scheduling it immediately.
     *
     * @param taskName unique logical name of the task to enable
     * @return updated {@link TaskStatusResponse}
     * @throws IllegalArgumentException if the task does not exist or is already enabled
     */
    @Transactional
    public TaskStatusResponse enableTask(String taskName) {
        TaskConfig config = findOrThrow(taskName);

        if (config.isEnabled()) {
            throw new IllegalArgumentException("Task '" + taskName + "' is already enabled.");
        }

        config.setEnabled(true);
        config = taskConfigRepository.save(config);

        // Update the registry and schedule the future
        registry.setEnabled(taskName, true);
        configurer.scheduleTask(taskName);

        log.info("Enabled task='{}'", taskName);
        return toResponse(config);
    }

    /**
     * Disables a running task, cancelling its scheduled future.
     *
     * <p>The task configuration is preserved in the database.  The task can be
     * re-enabled at any time via the {@link #enableTask} method.
     *
     * @param taskName unique logical name of the task to disable
     * @return updated {@link TaskStatusResponse}
     * @throws IllegalArgumentException if the task does not exist or is already disabled
     */
    @Transactional
    public TaskStatusResponse disableTask(String taskName) {
        TaskConfig config = findOrThrow(taskName);

        if (!config.isEnabled()) {
            throw new IllegalArgumentException("Task '" + taskName + "' is already disabled.");
        }

        config.setEnabled(false);
        config = taskConfigRepository.save(config);

        // Update the registry and cancel the scheduled future
        registry.setEnabled(taskName, false);
        registry.cancelFuture(taskName);

        log.info("Disabled task='{}'", taskName);
        return toResponse(config);
    }

    // ── Delete ────────────────────────────────────────────────────────────────────

    /**
     * Permanently deletes a task: cancels its scheduled future, removes it from
     * the in-memory registry, and deletes its database record.
     *
     * @param taskName unique logical name of the task to delete
     * @throws IllegalArgumentException if the task does not exist
     */
    @Transactional
    public void deleteTask(String taskName) {
        TaskConfig config = findOrThrow(taskName);

        // Cancel and remove from in-memory registry
        registry.remove(taskName);

        // Delete from the database
        taskConfigRepository.delete(config);

        log.info("Deleted task='{}'", taskName);
    }

    // ── List / Get ────────────────────────────────────────────────────────────────

    /**
     * Returns status information for all registered tasks.
     *
     * @return list of {@link TaskStatusResponse} DTOs, one per task
     */
    @Transactional(readOnly = true)
    public List<TaskStatusResponse> listTasks() {
        return taskConfigRepository.findAll()
                                   .stream()
                                   .map(this::toResponse)
                                   .toList();
    }

    /**
     * Returns status information for a single task identified by name.
     *
     * @param taskName unique logical name of the task
     * @return {@link TaskStatusResponse} for the named task
     * @throws IllegalArgumentException if the task does not exist
     */
    @Transactional(readOnly = true)
    public TaskStatusResponse getTask(String taskName) {
        return toResponse(findOrThrow(taskName));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────────

    /**
     * Looks up a {@link TaskConfig} by name or throws an {@link IllegalArgumentException}
     * (mapped to HTTP 404 by the global exception handler) if not found.
     *
     * @param taskName the task name to look up
     * @return the found entity
     */
    private TaskConfig findOrThrow(String taskName) {
        return taskConfigRepository.findByTaskName(taskName)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Task not found: '" + taskName + "'"));
    }

    /**
     * Converts a {@link TaskConfig} entity to the API response DTO, enriching it
     * with the live interval currently in the in-memory registry.
     *
     * <p>The live interval may differ from the database value briefly during the
     * propagation window after a REST update (ms-level window only).
     *
     * @param config the entity to convert
     * @return populated response DTO
     */
    private TaskStatusResponse toResponse(TaskConfig config) {
        // Read the live interval from registry; fall back to DB value if not yet registered
        long liveInterval = registry.getInterval(config.getTaskName(), config.getIntervalMs());

        return new TaskStatusResponse(
            config.getTaskName(),
            config.getDescription(),
            config.getIntervalMs(),
            liveInterval,
            config.isEnabled()
        );
    }
}
