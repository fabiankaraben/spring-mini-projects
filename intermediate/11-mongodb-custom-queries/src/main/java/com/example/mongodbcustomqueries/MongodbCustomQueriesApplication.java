package com.example.mongodbcustomqueries;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the MongoDB Custom Queries mini-project.
 *
 * <p>{@link SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks this as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – lets Spring Boot auto-configure beans
 *       based on the dependencies found on the classpath (e.g. Spring Data MongoDB,
 *       Spring Web MVC).</li>
 *   <li>{@code @ComponentScan} – scans this package and sub-packages for
 *       Spring-managed components ({@code @Controller}, {@code @Service}, etc.).</li>
 * </ul>
 *
 * <p>This project demonstrates how to use {@code MongoTemplate} for complex
 * aggregation queries that go beyond what simple Spring Data repository derived
 * methods can express. Topics covered:
 * <ul>
 *   <li>MongoDB Aggregation Pipeline with {@code $group}, {@code $match},
 *       {@code $sort}, {@code $limit}, {@code $project}, and {@code $unwind}.</li>
 *   <li>Grouping and computing statistics (sum, average, min, max, count).</li>
 *   <li>Filtering with {@code Criteria} at various pipeline stages.</li>
 *   <li>Projecting only specific fields in results.</li>
 *   <li>Combining multiple pipeline stages into a single aggregation.</li>
 * </ul>
 */
@SpringBootApplication
public class MongodbCustomQueriesApplication {

    public static void main(String[] args) {
        SpringApplication.run(MongodbCustomQueriesApplication.class, args);
    }
}
