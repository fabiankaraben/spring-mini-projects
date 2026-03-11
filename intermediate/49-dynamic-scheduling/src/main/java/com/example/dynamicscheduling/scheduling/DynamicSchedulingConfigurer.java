package com.example.dynamicscheduling.scheduling;

import com.example.dynamicscheduling.model.TaskConfig;
import com.example.dynamicscheduling.repository.TaskConfigRepository;
import com.example.dynamicscheduling.service.TaskExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Implements {@link SchedulingConfigurer} to register all background tasks
 * programmatically at application startup.
 *
 * <h2>Why SchedulingConfigurer instead of @Scheduled?</h2>
 * <p>The standard {@code @Scheduled(fixedRate = ...)} annotation bakes the
 * interval into bytecode at compile-time.  Once the application is running,
 * that value cannot be changed without a restart.
 *
 * <p>{@link SchedulingConfigurer} gives us access to the
 * {@link ScheduledTaskRegistrar} where we can register a
 * {@link org.springframework.scheduling.Trigger}-based task.  A {@code Trigger}'s
 * {@code nextExecution()} method is evaluated <em>after every execution</em>,
 * which means we can return a brand-new delay calculated from the live value
 * stored in {@link DynamicTaskRegistry}.  The next fire time is therefore always
 * based on whatever interval is current at that moment.
 *
 * <h2>Startup flow</h2>
 * <ol>
 *   <li>Spring calls {@link #configureTasks(ScheduledTaskRegistrar)} once after
 *       the application context is fully refreshed.</li>
 *   <li>We load all enabled {@link TaskConfig} rows from the database.</li>
 *   <li>For each config, we populate the {@link DynamicTaskRegistry} and register
 *       a trigger-based task with the task registrar.</li>
 *   <li>The trigger closure captures the task name; on every evaluation it reads
 *       the current interval from the registry so schedule changes are reflected
 *       on the very next cycle.</li>
 * </ol>
 */
@Component
public class DynamicSchedulingConfigurer implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchedulingConfigurer.class);

    /** Repository to load task configurations persisted in PostgreSQL. */
    private final TaskConfigRepository taskConfigRepository;

    /**
     * Service that contains the actual business logic each task should perform.
     * Injected here so the task runnable can call it.
     */
    private final TaskExecutionService taskExecutionService;

    /**
     * Spring's thread-pool-backed scheduler.  We store it so that
     * {@link com.example.dynamicscheduling.service.TaskManagementService}
     * can use it to schedule newly registered tasks after startup.
     */
    private final TaskScheduler taskScheduler;

    /** Registry that holds live interval and enabled-flag values. */
    private final DynamicTaskRegistry registry;

    public DynamicSchedulingConfigurer(TaskConfigRepository taskConfigRepository,
                                       TaskExecutionService taskExecutionService,
                                       TaskScheduler taskScheduler,
                                       DynamicTaskRegistry registry) {
        this.taskConfigRepository = taskConfigRepository;
        this.taskExecutionService = taskExecutionService;
        this.taskScheduler        = taskScheduler;
        this.registry             = registry;
    }

    // ── SchedulingConfigurer contract ─────────────────────────────────────────────

    /**
     * Called once by Spring after the application context is ready.
     * Loads all task configs from the database and registers a trigger-based
     * scheduled task for each one.
     *
     * @param registrar the task registrar provided by Spring
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // Tell the registrar to use our explicitly configured TaskScheduler bean.
        // Without this, Spring creates a single-threaded scheduler which can
        // cause all tasks to queue behind each other.
        registrar.setTaskScheduler(taskScheduler);

        // Load every task configuration persisted in the database
        List<TaskConfig> configs = taskConfigRepository.findAll();
        log.info("DynamicSchedulingConfigurer: found {} task config(s) in database", configs.size());

        for (TaskConfig config : configs) {
            registerTask(config);
        }
    }

    // ── Public helper – called by TaskManagementService for newly created tasks ───

    /**
     * Registers a single task with the registry and the underlying
     * {@link TaskScheduler}.
     *
     * <p>This method is also called by
     * {@link com.example.dynamicscheduling.service.TaskManagementService} when a
     * new task is created via the REST API after the application has started.
     *
     * @param config the task configuration to register
     */
    public void registerTask(TaskConfig config) {
        String taskName  = config.getTaskName();
        long   intervalMs = config.getIntervalMs();
        boolean enabled  = config.isEnabled();

        // Populate the in-memory registry so the trigger can read live values
        registry.setInterval(taskName, intervalMs);
        registry.setEnabled(taskName, enabled);

        log.info("Registering task='{}' intervalMs={} enabled={}", taskName, intervalMs, enabled);

        if (!enabled) {
            // Task is disabled in the database – do not schedule it yet.
            // It will be scheduled when the operator re-enables it via the REST API.
            log.info("Task='{}' is disabled – skipping scheduling", taskName);
            return;
        }

        scheduleTask(taskName);
    }

    /**
     * Submits the named task to the underlying {@link TaskScheduler} using a
     * trigger that reads the current interval from the {@link DynamicTaskRegistry}
     * on every next-execution evaluation.
     *
     * <p>The returned {@link ScheduledFuture} is stored in the registry so it can
     * be cancelled when the task is disabled or deleted.
     *
     * @param taskName the unique logical name of the task to schedule
     */
    public void scheduleTask(String taskName) {
        // Build a trigger that computes the next execution time dynamically.
        // Spring calls nextExecution() AFTER each execution completes, which is
        // exactly when we want to re-read the interval from the registry.
        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> taskExecutionService.executeTask(taskName),
            triggerContext -> {
                // Read the current interval from the registry (may have been updated by REST API)
                long currentIntervalMs = registry.getInterval(taskName, 5000L);

                // Determine the base time: last actual execution, or now if never run
                Instant lastCompletion = triggerContext.lastCompletion();
                Instant base = (lastCompletion != null) ? lastCompletion : Instant.now();

                // Schedule the next execution at base + currentInterval
                return base.plus(Duration.ofMillis(currentIntervalMs));
            }
        );

        // Store the future so it can be cancelled when the task is disabled/deleted
        registry.registerFuture(taskName, future);
        log.info("Scheduled task='{}' with initial interval={} ms",
                 taskName, registry.getInterval(taskName, 5000L));
    }
}
