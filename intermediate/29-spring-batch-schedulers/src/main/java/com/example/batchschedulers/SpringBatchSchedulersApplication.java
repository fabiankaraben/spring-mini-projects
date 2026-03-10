package com.example.batchschedulers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Spring Batch Schedulers mini-project.
 *
 * <p>This application demonstrates how to combine Spring Batch with Spring's
 * built-in scheduling mechanism ({@code @EnableScheduling} / {@code @Scheduled})
 * to trigger batch jobs automatically on a periodic basis.
 *
 * <p>Three batch jobs are defined:
 * <ul>
 *   <li><strong>priceRefreshJob</strong> – simulates refreshing product prices
 *       from an external source (runs every 30 seconds by default).</li>
 *   <li><strong>inventoryAuditJob</strong> – audits product inventory levels and
 *       flags items that are low in stock (runs every minute by default).</li>
 *   <li><strong>reportGenerationJob</strong> – generates a summary report of all
 *       products and stores it in the database (runs every 2 minutes by default).</li>
 * </ul>
 *
 * <p>Each job can also be triggered manually via the REST API.
 *
 * <p>{@code @EnableScheduling} activates the Spring scheduling infrastructure so
 * that {@code @Scheduled} methods on {@code @Component}/{@code @Service} beans
 * are detected and executed by a background thread pool.
 */
@SpringBootApplication
@EnableScheduling
public class SpringBatchSchedulersApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchSchedulersApplication.class, args);
    }
}
