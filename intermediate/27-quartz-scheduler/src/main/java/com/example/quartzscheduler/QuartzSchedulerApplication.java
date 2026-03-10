package com.example.quartzscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Quartz Scheduler Spring Boot application.
 *
 * <p>This application demonstrates how to integrate the Quartz job-scheduling
 * library with Spring Boot. It showcases:
 * <ul>
 *   <li>Persisting job definitions to PostgreSQL via Quartz's JDBC job-store
 *       so that scheduled jobs survive application restarts.</li>
 *   <li>Three distinct job types: a simple logging job, a data-cleanup job,
 *       and a report-generation job – each with its own cron expression.</li>
 *   <li>A REST API that allows clients to schedule, pause, resume, trigger
 *       immediately, and delete jobs at runtime without restarting the server.</li>
 *   <li>A custom audit-log table (persisted via Spring Data JPA) that records
 *       every job execution with its outcome and duration.</li>
 *   <li>Flyway database migrations to manage the Quartz DDL and the audit
 *       table schema in a reproducible, version-controlled way.</li>
 * </ul>
 */
@SpringBootApplication
public class QuartzSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuartzSchedulerApplication.class, args);
    }
}
