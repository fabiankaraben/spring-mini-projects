package com.example.dynamicscheduling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Spring configuration for the task scheduler thread pool.
 *
 * <h2>Why a custom TaskScheduler?</h2>
 * <p>Spring Boot auto-configures a {@link ThreadPoolTaskScheduler} when
 * {@code @EnableScheduling} is on the classpath, but it defaults to a pool
 * size of <em>1</em>.  With only one thread:
 * <ul>
 *   <li>If one task runs long, all other tasks queue up behind it.</li>
 *   <li>A deadlock could occur if two tasks need to run simultaneously.</li>
 * </ul>
 *
 * <p>By declaring our own {@link TaskScheduler} bean we control:
 * <ul>
 *   <li>Pool size – allows multiple tasks to execute concurrently.</li>
 *   <li>Thread name prefix – makes scheduler threads easy to identify in thread
 *       dumps and monitoring tools (they appear as {@code DynamicTask-1},
 *       {@code DynamicTask-2}, etc.).</li>
 *   <li>Error handler – logs uncaught exceptions thrown by task runnables instead
 *       of silently discarding them.</li>
 * </ul>
 *
 * <p>This bean is injected into {@link com.example.dynamicscheduling.scheduling.DynamicSchedulingConfigurer}
 * and used as the scheduler for all dynamic tasks.
 */
@Configuration
public class SchedulerConfig {

    /**
     * Creates the thread pool task scheduler shared by all dynamic tasks.
     *
     * <p>A pool size of 5 is sufficient for this demonstration (the seed data
     * creates 4 tasks), with one spare thread for ad-hoc operations.  In
     * production, size the pool according to the number of concurrent tasks
     * and their expected wall-clock durations.
     *
     * @return configured {@link TaskScheduler} bean
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // Number of threads available to run tasks concurrently.
        // Set to 5 so that all demo tasks can run in parallel without queuing.
        scheduler.setPoolSize(5);

        // Prefix for thread names – makes scheduler threads easy to spot in
        // logs, thread dumps, and monitoring dashboards.
        scheduler.setThreadNamePrefix("DynamicTask-");

        // Log uncaught exceptions from task runnables rather than silently swallowing them.
        // Without this, a RuntimeException thrown by a task would be lost.
        scheduler.setErrorHandler(t ->
            org.slf4j.LoggerFactory
                .getLogger(SchedulerConfig.class)
                .error("Uncaught exception in scheduled task", t)
        );

        // Call initialize() explicitly here so the scheduler is fully ready when
        // DynamicSchedulingConfigurer.configureTasks() is called at startup.
        scheduler.initialize();

        return scheduler;
    }
}
