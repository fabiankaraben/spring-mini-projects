package com.example.dynamicscheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Dynamic Scheduling Spring Boot application.
 *
 * <p>This application demonstrates how to modify {@code @Scheduled} task
 * frequencies <em>at runtime</em> without restarting the server.  Instead
 * of the fixed-rate or fixed-delay values that are baked into annotations
 * at compile-time, this project uses a combination of:
 *
 * <ul>
 *   <li>{@link org.springframework.scheduling.annotation.SchedulingConfigurer}
 *       – allows programmatic registration of tasks via a
 *       {@link org.springframework.scheduling.config.ScheduledTaskRegistrar}.</li>
 *   <li>A named task registry ({@code DynamicTaskRegistry}) – holds the
 *       current configuration (interval, enabled flag) for every task in an
 *       in-memory {@code ConcurrentHashMap}.</li>
 *   <li>A REST API – lets clients query and update schedules while the
 *       application is running.</li>
 *   <li>PostgreSQL + Flyway – persists task configuration and execution logs so
 *       the schedules survive application restarts.</li>
 * </ul>
 *
 * <p>{@link EnableScheduling} activates Spring's scheduling infrastructure.
 * Without it, {@code @Scheduled} annotations and
 * {@link org.springframework.scheduling.annotation.SchedulingConfigurer}
 * implementations are ignored.
 */
@SpringBootApplication
@EnableScheduling
public class DynamicSchedulingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicSchedulingApplication.class, args);
    }
}
